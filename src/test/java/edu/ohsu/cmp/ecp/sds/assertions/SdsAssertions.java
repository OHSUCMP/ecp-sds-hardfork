package edu.ohsu.cmp.ecp.sds.assertions;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;

import org.hl7.fhir.instance.model.api.IIdType;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.util.SdsLinkageOperations;
import edu.ohsu.cmp.ecp.sds.util.SdsSystemOperations;

public class SdsAssertions {
	private final LinkageAssertions linkageAssertions ;
	private final SystemAssertions systemAssertions ;
	private final SdsLinkageOperations sdsLinkageOps ;
	private final PartitionAssertions localPartitionAssertions ;
	private final Map<IIdType,PartitionAssertions> foreignPartitionAssertions =
		new TreeMap<>( FhirResourceComparison.idTypes().comparator() );

	private static final BiPredicate<IIdType,IIdType> ID_EQUALITY = (a,b) -> 0 == FhirResourceComparison.idTypes().comparator().compare(a,b) ;

	public SdsAssertions( IGenericClient localClient ) {
		this.systemAssertions = new SystemAssertions( new SdsSystemOperations( localClient ) ) ;
		this.sdsLinkageOps = new SdsLinkageOperations(localClient ) ;
		this.localPartitionAssertions = new PartitionAssertions( localClient, new UpdateableOnce<IIdType>("local patient id", Optional.empty(), ID_EQUALITY ), sdsLinkageOps ) ;
		this.linkageAssertions = new LinkageAssertions( sdsLinkageOps ) ;
	}

	public LinkageAssertions linkages() {
		return this.linkageAssertions ;
	}

	public SystemAssertions system() {
		return this.systemAssertions ;
	}

	public PartitionAssertions local() {
		return this.localPartitionAssertions ;
	}

	public PartitionAssertions foreign( IGenericClient foreginPartitionSpecificClient, IIdType foreignPatientId ) {
		return foreignPartitionAssertions
			.computeIfAbsent(
				foreignPatientId,
				id -> new PartitionAssertions( foreginPartitionSpecificClient, new UpdateableOnce<IIdType>("local patient id", Optional.of(id), ID_EQUALITY), sdsLinkageOps )
			) ;
	}
}