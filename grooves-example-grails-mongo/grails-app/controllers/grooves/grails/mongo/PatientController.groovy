package grooves.grails.mongo

import grails.converters.JSON
import grails.rx.web.RxController
import grails.transaction.Transactional

import static org.springframework.http.HttpStatus.NOT_FOUND

@Transactional(readOnly = true)
class PatientController implements RxController {

    def index(Integer max) {
        log.info "$params"
        params.max = Math.min(max ?: 10, 100)
        respond Patient.list(params), model: [patientCount: Patient.count()]
    }

    def show(Patient patient) {
        log.info "$params"
        respond patient
    }

    def account(Patient patient) {
        log.info "$params"
        def snapshot = params.version ?
                new PatientAccountQuery().computeSnapshot(patient, params.long('version')) :
                params['date'] ?
                        new PatientAccountQuery().computeSnapshot(patient, params.date('date')) :
                        new PatientAccountQuery().computeSnapshot(patient, Long.MAX_VALUE)

        snapshot.map {
            rx.respond(it)
        }
    }

    def health(Patient patient) {
        log.info "$params"
        def snapshot = params.version ?
                new PatientHealthQuery().computeSnapshot(patient, params.long('version')) :
                params['date'] ?
                        new PatientHealthQuery().computeSnapshot(patient, params.date('date')) :
                        new PatientHealthQuery().computeSnapshot(patient, Long.MAX_VALUE)

        snapshot.map { s ->
            JSON.use('deep') {
                rx.render(s as JSON)
            }
        }
    }

    protected void notFound() {
        log.info "$params"
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'patient.label', default: 'Patient'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }
}
