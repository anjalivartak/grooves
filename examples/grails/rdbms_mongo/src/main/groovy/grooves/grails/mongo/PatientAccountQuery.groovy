package grooves.grails.mongo

import com.github.rahulsom.grooves.api.EventApplyOutcome
import com.github.rahulsom.grooves.grails.GormQuerySupport
import com.github.rahulsom.grooves.groovy.transformations.Query
import grails.compiler.GrailsCompileStatic
import rx.Observable

import static com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE
import static rx.Observable.just

/**
 * Queries for the PatientAccount
 */
@Query(aggregate = Patient, snapshot = PatientAccount)
@GrailsCompileStatic
class PatientAccountQuery implements
        GormQuerySupport<Long, Patient, Long, PatientEvent, String, PatientAccount,
                PatientAccountQuery> {

    final Class<PatientAccount> snapshotClass = PatientAccount
    final Class<PatientEvent> eventClass = PatientEvent

    @Override
    PatientAccount createEmptySnapshot() { new PatientAccount(deprecates: []) }

    @Override
    boolean shouldEventsBeApplied(PatientAccount snapshot) {
        true
    }

    @Override
    void addToDeprecates(PatientAccount snapshot, Patient deprecatedAggregate) {
        snapshot.addToDeprecatesIds(deprecatedAggregate.id)
    }

    @Override
    Observable<EventApplyOutcome> onException(
            Exception e, PatientAccount snapshot, PatientEvent event) {
        snapshot.processingErrors << e.message
        just CONTINUE
    }

    Observable<EventApplyOutcome> applyPatientCreated(
            PatientCreated event, PatientAccount snapshot) {
        snapshot.name = snapshot.name ?: event.name
        just CONTINUE
    }

    Observable<EventApplyOutcome> applyProcedurePerformed(
            ProcedurePerformed event, PatientAccount snapshot) {
        snapshot.balance += event.cost
        just CONTINUE
    }

    Observable<EventApplyOutcome> applyPaymentMade(
            PaymentMade event, PatientAccount snapshot) {
        snapshot.balance -= event.amount
        snapshot.moneyMade += event.amount
        just CONTINUE
    }

    @SuppressWarnings(['DuplicateStringLiteral', 'UnusedMethodParameter',])
    Observable<EventApplyOutcome> applyPatientAddedToZipcode(
            PatientAddedToZipcode event, PatientAccount snapshot) {
        just CONTINUE // Ignore zip change
    }

    @Override
    PatientAccount detachSnapshot(PatientAccount snapshot) {
        if (snapshot.isAttached()) {
            snapshot.discard()
            snapshot.id = null
        }
        snapshot
    }

}
