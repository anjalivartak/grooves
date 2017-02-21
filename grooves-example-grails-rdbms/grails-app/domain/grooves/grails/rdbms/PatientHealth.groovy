package grooves.grails.rdbms

import com.github.rahulsom.grooves.api.Snapshot

class PatientHealth implements Snapshot<Patient> {

    Long lastEvent
    Patient deprecatedBy
    Set<Patient> deprecates
    Patient aggregate

    String name

    static hasMany = [
            deprecates: Patient,
            procedures: Procedure
    ]

    static constraints = {
    }
}

