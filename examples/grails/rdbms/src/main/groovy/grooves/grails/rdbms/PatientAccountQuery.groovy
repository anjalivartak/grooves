package grooves.grails.rdbms

import com.github.rahulsom.grooves.api.EventApplyOutcome
import com.github.rahulsom.grooves.grails.GormQuerySupport
import com.github.rahulsom.grooves.groovy.transformations.Query
import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import groovy.json.JsonSlurper
import rx.Observable

import static com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE
import static rx.Observable.just

/**
 * Performs a query for Patient Account information
 *
 * @author Rahul Somasunderam
 */
@Query(aggregate = Patient, snapshot = PatientAccount)
@GrailsCompileStatic
class PatientAccountQuery implements
        GormQuerySupport<Long, Patient, Long, PatientEvent, Long, PatientAccount,
                PatientAccountQuery> {

    @Override
    PatientAccount createEmptySnapshot() { new PatientAccount(deprecates: []) }

    @Override
    boolean shouldEventsBeApplied(PatientAccount snapshot) {
        true
    }

    @Override
    void addToDeprecates(PatientAccount snapshot, Patient deprecatedAggregate) {
        snapshot.addToDeprecates(deprecatedAggregate)
    }

    @Override
    Observable<EventApplyOutcome> onException(
            Exception e, PatientAccount snapshot, PatientEvent event) {
        // ignore exceptions. Look at the mongo equivalent to see one possible way to handle
        // exceptions
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

    final Class<PatientAccount> snapshotClass = PatientAccount
    final Class<PatientEvent> eventClass = PatientEvent

    @Override PatientAccount detachSnapshot(PatientAccount snapshot) {
        new JsonSlurper().parseText((snapshot as JSON).toString()) as PatientAccount
    }
}
