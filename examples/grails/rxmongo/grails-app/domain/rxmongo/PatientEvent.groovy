package rxmongo

import com.github.rahulsom.grooves.api.events.BaseEvent
import com.github.rahulsom.grooves.api.events.DeprecatedBy
import com.github.rahulsom.grooves.api.events.Deprecates
import com.github.rahulsom.grooves.api.events.RevertEvent
import com.github.rahulsom.grooves.groovy.transformations.Event
import grails.gorm.rx.mongodb.RxMongoEntity
import groovy.json.JsonBuilder
import groovy.transform.EqualsAndHashCode
import rx.Observable

import static rx.Observable.just

/**
 * Represents Patient Events
 *
 * @author Rahul Somasunderam
 */
@EqualsAndHashCode
@SuppressWarnings(['AbstractClassWithoutAbstractMethod', 'GrailsDomainReservedSqlKeywordName'])
class PatientEvent implements RxMongoEntity<PatientEvent>,
        BaseEvent<String, Patient, String, PatientEvent> {

    String id
    RevertEvent<String, Patient, String, PatientEvent> revertedBy
    String createdBy
    Date timestamp
    Long position
    Patient aggregate
    Observable<Patient> getAggregateObservable() { just(aggregate) }

    static transients = ['revertedBy']

    static constraints = {
    }
    @Override String toString() { "PatientEvent $id" }
    @Override String getAudit() { new JsonBuilder([name: name]).toString() }

}

@Event(Patient)
@EqualsAndHashCode
class PatientCreated extends PatientEvent {
    String name

    @Override String getAudit() { new JsonBuilder([name: name]).toString() }
    @Override String toString() { "<$id> created" }
}

@Event(Patient)
@EqualsAndHashCode
class ProcedurePerformed extends PatientEvent {
    String code
    BigDecimal cost

    @Override String getAudit() { new JsonBuilder([code: code, cost: cost]).toString() }
    @Override String toString() { "<$id> performed $code for $cost" }
}

@Event(Patient)
@EqualsAndHashCode
class PaymentMade extends PatientEvent {
    BigDecimal amount

    @Override String getAudit() { new JsonBuilder([amount: amount]).toString() }
    @Override String toString() { "<$id> paid $amount" }
}

@EqualsAndHashCode
class PatientEventReverted extends PatientEvent
        implements RevertEvent<String, Patient, String, PatientEvent> {
    String revertedEventId

    @Override String getAudit() { new JsonBuilder([revertedEvent: revertedEventId]).toString() }
    @Override String toString() { "<$id> reverted $revertedEventId" }
}

@EqualsAndHashCode
class PatientDeprecatedBy extends PatientEvent
        implements DeprecatedBy<String, Patient, String, PatientEvent> {
    PatientDeprecates converse
    Patient deprecator

    Observable<PatientDeprecates> getConverseObservable() { just(converse) }
    Observable<Patient> getDeprecatorObservable() { just(deprecator) }

    @Override String getAudit() { new JsonBuilder([deprecatedBy: deprecator.id]).toString() }
    @Override String toString() { "<$id> deprecated by #${deprecator.id}" }
}

@EqualsAndHashCode
class PatientDeprecates extends PatientEvent
        implements Deprecates<String, Patient, String, PatientEvent> {
    PatientDeprecatedBy converse
    Patient deprecated

    Observable<PatientDeprecatedBy> getConverseObservable() { just(converse) }
    Observable<Patient> getDeprecatedObservable() { just(deprecated) }

    @Override String getAudit() { new JsonBuilder([deprecates: deprecated.id]).toString() }
    @Override String toString() { "<$id> deprecates #${deprecated.id}" }
}
