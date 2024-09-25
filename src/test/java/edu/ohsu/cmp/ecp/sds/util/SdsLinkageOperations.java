package edu.ohsu.cmp.ecp.sds.util;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Linkage;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

public class SdsLinkageOperations {
	private final IGenericClient localClient ;

	public SdsLinkageOperations( IGenericClient localClient ) {
		this.localClient = localClient ;
	}

	public List<Linkage> searchByItem( IIdType patientId ) {
		Bundle linkageBundle =
			localClient.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId(patientId.toUnqualifiedVersionless()) )
				.returnBundle(Bundle.class)
				.execute()
				;

		List<Linkage> linkages =
			linkageBundle
				.getEntry().stream()
					.filter( Bundle.BundleEntryComponent::hasResource )
					.map( Bundle.BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.collect( toList() )
					;

		// check for incorrectly-indexed Linkage resources
		validateLinkagesFor( patientId, linkages ) ;

		return linkages ;
	}

	/*
	 * when Linkage resources are created PRIOR TO the
	 * resources they reference, then subsequent searches
	 * can fail to find the Linkage resources
	 * 
	 * this method checks a list of Linkages (presumably found by a specific search) against
	 * the expected list found by an exhaustive search
	 */
	private void validateLinkagesFor( IIdType patientId, List<Linkage> linkagesToValidate ) {
		Set<IIdType> linkageIdsToValidate = FhirResourceComparison.idTypes().createSet()  ;
		linkagesToValidate.stream().map( Linkage::getIdElement ).forEach( linkageIdsToValidate::add ) ;

		Bundle allLinkagesBundle =
			localClient.search()
				.forResource( Linkage.class )
				.where( Linkage.ITEM.hasId(patientId.toUnqualifiedVersionless()) )
				.returnBundle(Bundle.class)
				.execute()
				;
		List<Linkage> allLinkages =
			allLinkagesBundle
				.getEntry().stream()
					.filter( Bundle.BundleEntryComponent::hasResource )
					.map( Bundle.BundleEntryComponent::getResource )
					.filter( Linkage.class::isInstance )
					.map( Linkage.class::cast )
					.collect( toList() )
					;
		Set<IIdType> allLinkageIds = FhirResourceComparison.idTypes().createSet()  ;
		allLinkages.stream().map( Linkage::getIdElement ).forEach( allLinkageIds::add ) ;

		assertThat( "linkage resources different than resources found by post-filtering", linkageIdsToValidate, equalTo(allLinkageIds) ) ;
	}

}
