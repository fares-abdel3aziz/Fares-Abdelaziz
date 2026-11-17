package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Appointment
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BookingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleAppointment(customerName: String = "كريم طارق"): Appointment {
        return Appointment(
            customerName = customerName,
            customerPhone = "01029898309",
            barberId = "mody",
            barberName = "مودي",
            packageId = "vip_royal",
            packageTitle = "باكيدج أبو العطا الملكي (VIP)",
            packageServices = "قصة شعر وتدريج احترافي حسب اختيارك • تحديد ونحت الدقن بالموس والفوطة الساخنة • غسيل الشعر + استشوار وتصفيف وواكس • حمام بخار أوزون وتنظيف عميق للبشرة",
            selectedHaircut = "سكين فيد مع كويف عصري (Mid Skin Fade & Quiff)",
            appointmentDate = "اليوم (الإثنين) ١٦ نوفمبر",
            appointmentTime = "11:30 مساءً",
            appointmentEpochMillis = 1795374000000L,
            notes = "تدريج خفيف",
            reminderScheduled = true,
        )
    }

    @Test
    fun createAndObserveAppointments_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = BookingRepository(firestore)

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAppointment(sampleAppointment())
        }
        assertTrue(created.id.isNotEmpty())
        assertEquals(aliceUid, created.userId)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAppointments(aliceUid).first { list -> list.any { it.id == created.id } }
        }
        assertTrue(emitted.any { it.id == created.id && it.barberName == "مودي" })
    }

    @Test
    fun updateAppointmentStatus_toCancelled_succeedsAndLocksFurtherEdits() = runBlocking {
        val aliceUid = signInTestUser("alice_status_${UUID.randomUUID()}@test.com")
        val repository = BookingRepository(firestore)

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAppointment(sampleAppointment())
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateAppointmentStatus(created.id, "cancelled", reminderScheduled = false)
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getAppointmentById(created.id, aliceUid)
        }
        assertNotNull(updated)
        assertEquals("cancelled", updated?.status)

        // Attempting another status transition after terminal state 'cancelled' must be rejected
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.updateAppointmentStatus(created.id, "completed", reminderScheduled = false)
            }
            fail("Expected PERMISSION_DENIED when updating a cancelled appointment")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun getAppointmentById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val repository = BookingRepository(firestore)
        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAppointment(sampleAppointment())
        }

        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.getAppointmentById(created.id, aliceUid)
            }
            fail("Expected cross-user PII read to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun observeAppointments_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_unauth_${UUID.randomUUID()}@test.com")
        auth.signOut()
        val repository = BookingRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAppointments(aliceUid).first()
            }
            fail("Expected unauthenticated observeAppointments to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    @Test
    fun submitAppointmentFeedback_completedAppointment_persistsRatingAndRejectsUncompleted() = runBlocking {
        val aliceUid = signInTestUser("alice_feedback_${UUID.randomUUID()}@test.com")
        val repository = BookingRepository(firestore)

        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAppointment(sampleAppointment())
        }

        // Submitting feedback while still 'confirmed' must be rejected by Firestore rules
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.submitAppointmentFeedback(created.id, 5, "تقييم مبكر")
            }
            fail("Expected PERMISSION_DENIED when rating a non-completed appointment")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }

        // Mark completed, then submit 5-star feedback
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateAppointmentStatus(created.id, "completed", reminderScheduled = false)
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitAppointmentFeedback(
                appointmentId = created.id,
                rating = 5,
                feedbackComment = "تسلم إيد كابتن مودي، خدمة ممتازة",
            )
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getAppointmentById(created.id, aliceUid)
        }
        assertNotNull(updated)
        assertEquals("completed", updated?.status)
        assertEquals(5L, updated?.rating)
        assertEquals("تسلم إيد كابتن مودي، خدمة ممتازة", updated?.feedbackComment)
    }

    @Test
    fun observeAllSalonBookingsForOwner_regularCustomer_failsWithPermissionDenied() = runBlocking {
        signInTestUser("alice_customer_${UUID.randomUUID()}@test.com")
        val repository = BookingRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createAppointment(sampleAppointment())
        }

        // Another regular customer (not Salon Owner) tries to list /salon_bookings
        signInTestUser("bob_customer_${UUID.randomUUID()}@test.com")
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAllSalonBookingsForOwner().first()
            }
            fail("Expected non-owner customer observeAllSalonBookingsForOwner to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

