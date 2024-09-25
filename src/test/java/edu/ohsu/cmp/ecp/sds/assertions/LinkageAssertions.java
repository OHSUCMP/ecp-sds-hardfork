package edu.ohsu.cmp.ecp.sds.assertions;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Linkage;
import org.hl7.fhir.r4.model.Reference;
import org.opentest4j.AssertionFailedError;

import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;
import edu.ohsu.cmp.ecp.sds.util.SdsLinkageOperations;

public class LinkageAssertions {
	private final SdsLinkageOperations sdsLinkageOps ;

	public LinkageAssertions( SdsLinkageOperations sdsLinkageOperations ) {
		this.sdsLinkageOps = sdsLinkageOperations ;
	}

	public SdsLinkageOperations operations() {
		return sdsLinkageOps;
	}

	public void assertAbsent( IIdType patientId ) {
		List<Linkage> linkages = sdsLinkageOps.searchByItem( patientId ) ;
		assertThat( linkages, empty() ) ;
	}

	public IIdType assertUniqueSourceItemPresent( IIdType patientId ) {
		List<Linkage> linkages = sdsLinkageOps.searchByItem( patientId ) ;
		Set<IIdType> sourceItemPatientIds =
			linkages.stream()
				.map( Linkage::getItem )
				.flatMap( List::stream )
				.filter( i -> i.getType().equals( Linkage.LinkageType.SOURCE ) )
				.map( Linkage.LinkageItemComponent::getResource )
				.filter( Reference.class::isInstance )
				.map( Reference.class::cast )
				.map( Reference::getReferenceElement )
				.collect( toSet() )
				;
		if ( sourceItemPatientIds.size() > 1 )
			throw new AssertionFailedError( "expected one (1) unique source Item among (" + linkages.size() + ") Linkage(s), but found " + sourceItemPatientIds.size() + ": " + sourceItemPatientIds ) ;
		if ( sourceItemPatientIds.size() == 0 )
			throw new AssertionFailedError( "expected one (1) unique source Item among (" + linkages.size() + ") Linkage(s), but found none" ) ;
		return sourceItemPatientIds.iterator().next() ;
	}

	public List<Linkage> assertPresent( IIdType patientId ) {
		List<Linkage> linkages = sdsLinkageOps.searchByItem( patientId ) ;
		assertThat( linkages, not( empty() ) ) ;
		return linkages ;
	}

	public List<Linkage> assertLinked( IIdType patientId, IIdType expectedLinkedPatientId ) {
		List<Linkage> linkages = assertPresent( patientId ) ;

		Predicate<Linkage.LinkageItemComponent> itemMatches = item -> {
				IIdType idReferencedByItem = item.getResource().getReferenceElement();
				return 0 == FhirResourceComparison.idTypes().comparator().compare( idReferencedByItem, expectedLinkedPatientId ) ;
			};
		List<Linkage> linkagesMatchingExpected =
			linkages.stream()
				.filter( k -> k.getItem().stream().anyMatch( itemMatches ) )
				.collect( toList() )
				;
		assertThat( linkagesMatchingExpected, not( empty() )) ;

		return linkagesMatchingExpected ;
	}

}