package edu.ohsu.cmp.ecp.sds;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPermissionRegistry;
import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import edu.ohsu.cmp.ecp.sds.assertions.PartitionAssertions;
import edu.ohsu.cmp.ecp.sds.assertions.SdsAssertions;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations.IdGenerationStrategy;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations.ResourceOperations;
import junit.framework.AssertionFailedError;

@ActiveProfiles( "auth-aware-test")
public class PatientAppClientExpungeTest extends BaseSuppplementalDataStoreTest {

	/*
	 * Use Case: expunge foreign partitions and local partition
	 */

	@Autowired
	AppTestMockPrincipalRegistry mockPrincipalRegistry ;

	@Autowired
	AppTestMockPermissionRegistry mockPermissionRegistry ;

	private static final String FOREIGN_PARTITION_A_NAME = "http://my.ehr.org/fhir/R4/" ;
	private static final String FOREIGN_PARTITION_OTHER_NAME = "http://community.ehr.net/" ;

	private IIdType authorizedPatientId;
	private IIdType foreignPatientIdOther;

	private SdsAssertions sdsAssertions ;
	private PartitionAssertions localPartition ;
	private PartitionAssertions foreignPartition1 ;
	private PartitionAssertions foreignPartition2 ;

	private final IdGenerationStrategy testSpecificIdGenerationStrategy = new IdGenerationStrategy() {
		@Override
		public IIdType generateId(Class<? extends IBaseResource> resourceType) {
			return new IdType( resourceType.getClass().getSimpleName(), createTestSpecificId() ) ;
		}
	} ;

	@BeforeEach
	public void setupAuthorizedPatient() {
		authorizedPatientId = new IdType( FOREIGN_PARTITION_A_NAME, "Patient", createTestSpecificId(), null ) ;
		String token = mockPrincipalRegistry.register().principal( "FOREIGN_A", authorizedPatientId.toString() ).token() ;

		foreignPatientIdOther = new IdType( FOREIGN_PARTITION_OTHER_NAME, "Patient", createTestSpecificId(), null );

		IGenericClient localClient = authenticatingClient( token ) ;
		IGenericClient foreignClient1 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_A_NAME ) ;
		IGenericClient foreignClient2 = authenticatingClientTargetingPartition( token, FOREIGN_PARTITION_OTHER_NAME ) ;

		sdsAssertions = new SdsAssertions( localClient ) ;
		localPartition = sdsAssertions.local() ;
		foreignPartition1 = sdsAssertions.foreign( foreignClient1, authorizedPatientId ) ;
		foreignPartition2 = sdsAssertions.foreign( foreignClient2, foreignPatientIdOther ) ;
		/* always generate a new randomized resource id for resources created in these partitions */
		foreignPartition1.operations().with( testSpecificIdGenerationStrategy );
		foreignPartition2.operations().with( testSpecificIdGenerationStrategy ) ;
	}

	@Test
	void canSoftDeleteNonLocalResourcesFromAuthorizedPartitionWhenLocalPartitionIsEmpty() {
		foreignPartition1.operations().patient().create() ;
		foreignPartition1.operations().resources( Condition.class ).create( configureCondition() ) ;
		foreignPartition1.operations().resources( Observation.class ).create( configureObservation() ) ;

		foreignPartition1.assertClaimed() ;
		foreignPartition1.linkages().assertAbsent();

		foreignPartition1.operations().patient().deleteCascade() ;

		foreignPartition1.assertClaimedThenDeleted() ;
		foreignPartition1.linkages().assertAbsent();
	}

	@Test
	void canSoftDeleteAndPurgeNonLocalResourcesFromAuthorizedPartitionWhenLocalPartitionIsEmpty() {
		foreignPartition1.operations().patient().create() ;

		foreignPartition1.assertClaimed() ;
		foreignPartition1.linkages().assertAbsent();

		foreignPartition1.operations().patient().deleteCascade() ;

		foreignPartition1.assertClaimedThenDeleted() ;
		foreignPartition1.linkages().assertAbsent();

		foreignPartition1.operations().patient().expungeOperation() ;

		foreignPartition1.waitForPatientToBecomeAbsent( Duration.ofSeconds(30), Duration.ofSeconds(600) ) ;
		foreignPartition1.assertUnclaimed() ;
	}

	@Test
	void cannotSoftDeleteNonLocalResourcesFromAuthorizedPartitionWhenLocalPartitionIsPopulated() {
		foreignPartition1.operations().patient().create() ;
		localPartition.operations().patient().create() ;

		foreignPartition1.assertClaimed() ;
		localPartition.assertClaimed() ;
		localPartition.linkages().assertPresentAndLinkedTo( authorizedPatientId ) ;

		assertThrows( Throwable.class, () -> {
			foreignPartition1.operations().patient().deleteCascade() ;
		});

		foreignPartition1.assertClaimed() ;
		localPartition.assertClaimed() ;
		localPartition.linkages().assertPresentAndLinkedTo( authorizedPatientId ) ;
	}

	@Test
	void canHardDeleteNonLocalResourcesFromAuthorizedPartitionWhenLocalPartitionIsEmpty() {
		foreignPartition1.operations().patient().create() ;

		foreignPartition1.assertClaimed() ;
		foreignPartition1.linkages().assertAbsent();

		foreignPartition1.operations().patient().deleteWithExpunge() ;

		foreignPartition1.waitForPatientToBecomeAbsent( Duration.ofSeconds(30), Duration.ofSeconds(600) ) ;
		foreignPartition1.assertUnclaimed() ; // as if it were never there
		foreignPartition1.linkages().assertAbsent();
	}

	@Test
	void cannotExpungeEverything() {

		assertThrows( ForbiddenOperationException.class, () -> {
			sdsAssertions.system().operations().expungeEverythingOperation() ;
		});

	}

	@Test
	void cannotExpungeAtTheSystemLevel() {

		assertThrows( ForbiddenOperationException.class, () -> {
			sdsAssertions.system().operations().expungeOperation() ;
		});

	}
	
	@Test
	void canCreateLocalAndForeignResourcesAndHardDeleteThemThenCreateMoreResources() {
		IIdType localPatientId = localPartition.operations().patient().create() ;
		localPartition.assertClaimed() ;
		localPartition.operations().resources( Condition.class ).create( configureCondition() ) ;
		localPartition.operations().resources( Observation.class ).create( configureObservation() ) ;

		foreignPartition1.operations().patient().create() ;
		foreignPartition1.assertClaimed() ;
		foreignPartition1.operations().resources( Condition.class ).create( configureCondition() ) ;
		foreignPartition1.operations().resources( Observation.class ).create( configureObservation() ) ;

		foreignPartition1.linkages().assertPresentAndLinkedTo( localPatientId );
		localPartition.linkages().assertPresentAndLinkedTo( authorizedPatientId );

		localPartition.operations().patient().deleteCascade() ;
		foreignPartition1.operations().patient().deleteCascade() ;

		localPartition.operations().patient().expungeOperation() ;
		foreignPartition1.operations().patient().expungeOperation() ;

		assertThrows( ForbiddenOperationException.class, () -> {
			localPartition.waitForPatientToBecomeAbsent( Duration.ofSeconds(30), Duration.ofSeconds(600) ) ;
		});
		foreignPartition1.waitForPatientToBecomeAbsent( Duration.ofSeconds(30), Duration.ofSeconds(600) ) ;

		IIdType localPatientIdAfterExpunge = localPartition.operations().patient().create() ;
		foreignPartition1.linkages().assertPresentAndLinkedTo( localPatientIdAfterExpunge );
	}


	@Test
	@Disabled("$delete-expunge authorization not yet implemented")
	void canHardDeleteInOneStepNonLocalResourcesFromAuthorizedPartitionWhenLocalPartitionIsEmpty() {
		foreignPartition1.operations().patient().create() ;

		foreignPartition1.assertClaimed() ;
		foreignPartition1.linkages().assertAbsent();

		foreignPartition1.operations().patient().deleteExpungeOperation() ;

		foreignPartition1.waitForPatientToBecomeAbsent( Duration.ofSeconds(30), Duration.ofSeconds(600) ) ;
		foreignPartition1.assertUnclaimed() ; // as if it were never there
		foreignPartition1.linkages().assertAbsent();
	}

	private Consumer<QuestionnaireResponse> configureQuestionnaireResponse( IIdType subject ) {
		return (questionnaireResponse) -> {
			questionnaireResponse.setSubject( new Reference( subject ) ) ;
			questionnaireResponse.setQuestionnaire( createTestSpecificId() ) ;
		};
	}

	private BiConsumer<SdsPartitionOperations, QuestionnaireResponse> configureQuestionnaireResponse() {
		return (ops, questionnaireResponse) -> {
			IIdType patientId = ops.id().orElseThrow(AssertionFailedError.class) ;
			questionnaireResponse.setSubject( new Reference( patientId ) ) ;
			questionnaireResponse.setQuestionnaire( createTestSpecificId() ) ;
		};
	}

	private Consumer<Condition> configureCondition( IIdType subject ) {
		return (condition) -> {
			condition.setId( new IdType( "Condition", createTestSpecificId() ) ) ;
			condition.setSubject( new Reference( subject ) ) ;
		};
	}

	private BiConsumer<SdsPartitionOperations, Condition> configureCondition() {
		return (ops, condition) -> {
			IIdType patientId = ops.id().orElseThrow(AssertionFailedError.class) ;
			condition.setSubject( new Reference( patientId ) ) ;
		};
	}

	private Consumer<Observation> configureObservation( IIdType subject ) {
		return (observation) -> {
			observation.setSubject( new Reference( subject ) ) ;
		};
	}

	private BiConsumer<SdsPartitionOperations,Observation> configureObservation() {
		return (ops,observation) -> {
			IIdType patientId = ops.id().orElseThrow(AssertionFailedError.class) ;
			observation.setSubject( new Reference( patientId ) ) ;
		};
	}

	@Test
	@Disabled("just a code snippet for now")
	void canExpungeLocalResources() {
		IIdType localPatientId = localPartition.operations().patient().create() ;

		Patient localPatientBeforeDelete = localPartition.operations().patient().read();
		assertThat( localPatientBeforeDelete, notNullValue() ) ;

		ResourceOperations<QuestionnaireResponse> localQuestionaireResponseOps =
			localPartition.operations().resources( QuestionnaireResponse.class );

		IIdType localQuestionnaireResponseId =
			localQuestionaireResponseOps.create( configureQuestionnaireResponse( localPatientId ) ) ;
		localQuestionaireResponseOps.create( configureQuestionnaireResponse( localPatientId ) ) ;
		localQuestionaireResponseOps.create( configureQuestionnaireResponse( localPatientId ) ) ;

		localQuestionaireResponseOps.read( localQuestionnaireResponseId ) ;

		List<QuestionnaireResponse> questionnaireResponses =
			localQuestionaireResponseOps
				.search( q -> q.where( QuestionnaireResponse.SUBJECT.hasId(localPatientId) ) )
				;
		assertThat( questionnaireResponses, hasSize( 3 ) ) ;

		localPartition.operations().patient().deleteExpungeOperation() ;

//		assertThrows( ResourceGoneException.class, () -> {
//			localClient.search().forResource(QuestionnaireResponse.class).where(QuestionnaireResponse.SUBJECT.hasId(authorizedPatientId)).execute() ;
//		});

		List<QuestionnaireResponse> questionnaireResponsesAfterDelete =
			localQuestionaireResponseOps
				.search( q -> q.where( QuestionnaireResponse.SUBJECT.hasId(localPatientId) ) )
				;
		assertThat( questionnaireResponsesAfterDelete, hasSize( 0 ) ) ;

		localPartition.assertClaimedThenDeleted();
		localPartition.assertSoftDeleted( QuestionnaireResponse.class, localQuestionnaireResponseId );
	}

}
