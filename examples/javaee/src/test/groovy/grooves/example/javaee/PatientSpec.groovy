package grooves.example.javaee

import com.github.rahulsom.grooves.test.AbstractPatientSpec
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient

/**
 * Acceptance test that tests against Springboot with JPA
 *
 * @author Rahul Somasunderam
 */
class PatientSpec extends AbstractPatientSpec {

    @Override
    RESTClient getRest() {
        new RESTClient("http://localhost:8080/grooves-jee/", ContentType.JSON)
    }
}
