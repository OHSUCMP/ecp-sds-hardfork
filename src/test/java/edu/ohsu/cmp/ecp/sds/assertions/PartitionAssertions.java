package edu.ohsu.cmp.ecp.sds.assertions;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.function.Supplier;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Patient;
import org.opentest4j.AssertionFailedError;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceGoneException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import edu.ohsu.cmp.ecp.sds.util.SdsLinkageOperations;
import edu.ohsu.cmp.ecp.sds.util.SdsPartitionOperations;

public class PartitionAssertions {
	private final SdsPartitionOperations sdsPartitionOps ;
	private final PartitionLinkageAssertions partitionLinkageAssertions ;

	public PartitionAssertions( IGenericClient client, UpdateableOnce<IIdType> patientId, SdsLinkageOperations sdsLinkageOperations ) {
		this.sdsPartitionOps = new SdsPartitionOperations( client, patientId ) ;
		this.partitionLinkageAssertions = new PartitionLinkageAssertions( this.sdsPartitionOps.id(), sdsLinkageOperations ) ;
	}

	public UpdateableOnce<IIdType> id() {
		return sdsPartitionOps.id();
	}

	public SdsPartitionOperations operations() {
		return sdsPartitionOps ;
	}

	public PartitionLinkageAssertions linkages() {
		return partitionLinkageAssertions ;
	}
	
	public void assertUnclaimedAndUnlinked() {
		assertUnclaimed() ;
		partitionLinkageAssertions.assertAbsent();
	}
	
	public void assertUnclaimed() {
		if ( !sdsPartitionOps.id().value().isPresent() ) {
			throw new AssertionFailedError("assertUnclaimed() requires a patient id; try using '.create(...)' or .id().update(...)' first");
		}
		assertThrows( ResourceNotFoundException.class, () -> {
			sdsPartitionOps.patient().read() ;
		});
	}

	public void waitForPatientToBecomePresent( Duration pollingInterval, Duration timeout ) {
		waitFor( pollingInterval, timeout, "patient to be present", () -> {
			try {
				sdsPartitionOps.patient().read() ;
				return true ;
			} catch ( ResourceNotFoundException | ResourceGoneException ex ) {
				return false ;
			}
		}) ;
	}

	public void waitForPatientToBecomeAbsent( Duration pollingInterval, Duration timeout ) {
		waitFor( pollingInterval, timeout, "patient to be absent", () -> {
			try {
				sdsPartitionOps.patient().read() ;
				return false ;
			} catch ( ResourceNotFoundException | ResourceGoneException ex ) {
				return true ;
			}
		}) ;
	}

	private void waitFor( Duration pollingInterval, Duration timeout, String description, Supplier<Boolean> condition ) {
		final long maxMilliseconds = timeout.toMillis() ;
		final long start = System.currentTimeMillis() ;
		boolean conditionFulfilled = false ;
		do {
			try {
				Thread.sleep( pollingInterval.toMillis() ) ;
				conditionFulfilled = condition.get() ;
			} catch (InterruptedException ex) {
				/* continue waiting */;
			}
		} while ( !conditionFulfilled && (System.currentTimeMillis() - start) < maxMilliseconds ) ;
		assertThat( "exceeded timeout waiting for " + description , conditionFulfilled, equalTo(true) );
	}
	
	public void assertClaimedThenDeleted() {
		assertThrows( ResourceGoneException.class, () -> {
			sdsPartitionOps.patient().read() ;
		});
	}

	public <T extends IBaseResource> void assertSoftDeleted( Class<T> resourceType, IIdType resourceId ) {
		assertThrows( ResourceGoneException.class, () -> {
			sdsPartitionOps. read( resourceType, resourceId );
		});
	}

	public Patient assertClaimed() {
		Patient patient = sdsPartitionOps.patient().read() ;
		assertThat( patient, notNullValue() ) ;
		return patient ;
	}

}