package grooves.grails.rdbms

import com.github.rahulsom.grooves.annotations.Query
import com.github.rahulsom.grooves.api.EventApplyOutcome
import com.github.rahulsom.grooves.api.QueryUtil
import com.github.rahulsom.grooves.grails.GormQueryUtil
import grails.compiler.GrailsCompileStatic
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil

import static com.github.rahulsom.grooves.api.EventApplyOutcome.CONTINUE

@Query(aggregate = Patient, snapshot = PatientHealth)
@GrailsCompileStatic
class PatientHealthQuery extends GormQueryUtil<Patient, PatientEvent, PatientHealth> {

    PatientHealthQuery() {
        super(Patient, PatientEvent, PatientHealth)
    }

    @Override
    PatientHealth createEmptySnapshot() { new PatientHealth(deprecates: []) }

    @Override
    boolean shouldEventsBeApplied(PatientHealth snapshot) {
        true
    }

    @Override
    void addToDeprecates(PatientHealth snapshot, Patient otherAggregate) {
        snapshot.addToDeprecates(otherAggregate)
    }

    @Override
    PatientEvent unwrapIfProxy(PatientEvent event) {
        GrailsHibernateUtil.unwrapIfProxy(event) as PatientEvent
    }

    @Override
    EventApplyOutcome onException(Exception e, PatientHealth snapshot, PatientEvent event) {
        // ignore exceptions. Look at the mongo equivalent to see one possible way to handle exceptions
        CONTINUE
    }

    EventApplyOutcome applyPatientCreated(PatientCreated event, PatientHealth snapshot) {
        snapshot.name = event.name
        CONTINUE
    }

    EventApplyOutcome applyProcedurePerformed(ProcedurePerformed event, PatientHealth snapshot) {
        snapshot.addToProcedures(code: event.code, date: event.date)
        CONTINUE
    }

    EventApplyOutcome applyPaymentMade(PaymentMade event, PatientHealth snapshot) {
        // Ignore payments
        CONTINUE
    }

}