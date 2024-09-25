package edu.ohsu.cmp.ecp.sds;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import ca.uhn.fhir.jpa.starter.AppTestMockPermissionRegistry;
import ca.uhn.fhir.jpa.starter.AppTestMockPrincipalRegistry;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import edu.ohsu.cmp.ecp.sds.assertions.PartitionAssertions;
import edu.ohsu.cmp.ecp.sds.assertions.SdsAssertions;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations.IdGenerationStrategy;
import junit.framework.AssertionFailedError;

@ActiveProfiles( "auth-aware-test")
public class PatientAppClientSimulationTest extends BaseSuppplementalDataStoreTest {

	/*
	 * Use Case: share data from other EHRs into the SDS, like the patient app does
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
	void canShareForeignPartitionForAuthorizedPatient() {

		/* store local patient */
		localPartition.operations().patient().create() ;

		/* store foreign partition 1 */
		foreignPartition1.operations().patient().create() ;

		List<Runnable> resourceTasks = partitionResourceCreationTasks( foreignPartition1.operations() ) ;
		resourceTasks.forEach( Runnable::run ) ;

		foreignPartition1.assertClaimed() ;
		List<Linkage> linkages = foreignPartition1.linkages().assertPresent() ;
		assertThat( linkages, hasSize(1) ) ;
	}

	@Test
	void canShareForeignPartitionOtherThanForAuthorizedPatient() {

		/* store local patient */
		localPartition.operations().patient().create() ;

		/* store foreign partition 2 */
		foreignPartition2.operations().patient().create() ;

		List<Runnable> resourceTasks = partitionResourceCreationTasks( foreignPartition2.operations() ) ;
		resourceTasks.forEach( Runnable::run ) ;
		foreignPartition2.assertClaimed() ;
		List<Linkage> linkages = foreignPartition2.linkages().assertPresent() ;
		assertThat( linkages, hasSize(1) ) ;
	}

	@Test
	void canShareMultipleForeignPartitions() {

		/* store local patient */
		localPartition.operations().patient().create() ;

		/* store foreign partition 1 */
		foreignPartition1.operations().patient().create() ;

		List<Runnable> resourceTasks1 = partitionResourceCreationTasks( foreignPartition1.operations() ) ;
		resourceTasks1.forEach( Runnable::run ) ;

		foreignPartition1.assertClaimed() ;
		List<Linkage> linkages1 = foreignPartition1.linkages().assertPresent() ;
		assertThat( linkages1, hasSize(1) ) ;

		/* store foreign partition 2 */
		foreignPartition2.operations().patient().create() ;

		List<Runnable> resourceTasks2 = partitionResourceCreationTasks( foreignPartition2.operations() ) ;
		resourceTasks2.forEach( Runnable::run ) ;
		foreignPartition2.assertClaimed() ;
		List<Linkage> linkages2 = foreignPartition2.linkages().assertPresent() ;
		assertThat( linkages2, hasSize(1) ) ;
	}

	private List<Runnable> partitionResourceCreationTasks(SdsPartitionOperations ops) {
		List<Runnable> resourceTasks = new ArrayList<>() ;
		for ( int i = 0 ; i < 10 ; ++i ) {
			resourceTasks.add( () -> {
				ops.resources( Condition.class ).create( configureCondition() ) ;
				ops.resources( Observation.class ).create( configureObservation() ) ;
			});
		}
		return resourceTasks ;
	}

	private BiConsumer<SdsPartitionOperations, Condition> configureCondition() {
		return (ops, condition) -> {
			IIdType patientId = ops.id().orElseThrow(AssertionFailedError.class) ;
			condition.setSubject( new Reference( patientId ) ) ;
		};
	}

	private BiConsumer<SdsPartitionOperations,Observation> configureObservation() {
		return (ops,observation) -> {
			IIdType patientId = ops.id().orElseThrow(AssertionFailedError.class) ;
			observation.setSubject( new Reference( patientId ) ) ;
		};
	}
}
