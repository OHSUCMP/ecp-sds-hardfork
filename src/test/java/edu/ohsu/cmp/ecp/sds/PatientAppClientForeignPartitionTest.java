package edu.ohsu.cmp.ecp.sds;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreMatchers.identifiesResource;
import static edu.ohsu.cmp.ecp.sds.SupplementalDataStoreMatchers.identifiesSameResourceAs;

import java.util.List;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPermissionRegistry;
import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import junit.framework.AssertionFailedError;

@ActiveProfiles( "auth-aware-test")
public class PatientAppClientForeignPartitionTest extends BaseSuppplementalDataStoreTest {

	/*
	 * Use Case: store NON-LOCAL Patient and other resources in the patient compartment
	 */

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;
	
	@Autowired
	AppTestMockPermissionRegistry mockPermissionRegistry ;
	
	private static final String FOREIGN_PARTITION_NAME = "http://my.ehr.org/fhir/R4/" ;

	private IIdType authorizedPatientId;
	private IGenericClient patientAppClient ;

	@BeforeEach
	public void setupAuthorizedPatient() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_NAME, "Patient", createTestSpecificId(), null );
		String token = mockPrincipalRegistry.register().principal( "MyPatient", authorizedPatientId.toString() ).token() ;

		patientAppClient = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_NAME ) ;
	}

	@Test
	void canStoreAndReadPatientThatIsAuthorizedPatient() {
		Patient patient = new Patient() ;
		patient.setId( new IdType( "Patient", authorizedPatientId.getIdPart() ) ) ;
		IIdType patientId = patientAppClient.update().resource(patient).execute().getId();

		assertThat( patientId, identifiesResource( patient ) );

		Patient readPatient = patientAppClient.read().resource(Patient.class).withId( patientId ).execute();

		assertThat( readPatient, notNullValue() );
		assertThat( readPatient.getIdElement(), identifiesSameResourceAs( patient.getIdElement() ) );
	}

	@Test
	void canStorePatientWithoutAdditionalSetup() {
		String subjectPatientId = createTestSpecificId() ;
		Patient subjectPatient = new Patient() ;
		subjectPatient.setId( new IdType( "Patient", subjectPatientId ) ) ;
		
		IIdType claimedPatientId = patientAppClient.update().resource(subjectPatient).execute().getId();

		Patient readPatientResp = patientAppClient.read().resource(Patient.class).withId(claimedPatientId).execute();
		
		Assertions.assertNotNull( readPatientResp );
	}

	private Condition createHealthConcern( Reference subjectRef, String codeAsPlainText ) {
		Condition condition  = new Condition() ;
		condition.setId( new IdType("Condition", createTestSpecificId()) ) ;

		condition.setSubject( subjectRef ) ;
		
		CodeableConcept healthConcernCategory = new CodeableConcept();
		healthConcernCategory.addCoding( new Coding( "http://hl7.org/fhir/ValueSet/condition-category", "health-concern", "health concern" ) ) ;
		condition.setCategory( asList( healthConcernCategory ) ) ;
		
		condition.setCode( healthConcernCategory.setText(codeAsPlainText) ) ;
		
		return condition ;
	}

	@Test
	void canStoreConditionWhereSubjectIsClaimedPatientWithoutAdditionalSetup() {
		String subjectPatientId = createTestSpecificId() ;
		Patient subjectPatient = new Patient() ;
		subjectPatient.setId( new IdType( "Patient", subjectPatientId ) ) ;
		
		patientAppClient.update().resource(subjectPatient).execute();
		
		Condition condition  = createHealthConcern( new Reference( subjectPatient.getIdElement() ), "my health concern" ) ;
		
		IIdType conditionId = patientAppClient.update().resource(condition).execute().getId();
		
		Condition readQuestResp = patientAppClient.read().resource(Condition.class).withId(conditionId).execute();
		
		Assertions.assertNotNull( readQuestResp );
	}

}
