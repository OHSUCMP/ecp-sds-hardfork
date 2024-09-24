package edu.ohsu.cmp.ecp.sds;

import static java.util.stream.Collectors.joining;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IIdType;

import edu.ohsu.cmp.ecp.sds.base.FhirResourceComparison;

public final class UserIdentity {
	private final String userResourceType;
	private final IIdType basisUserId;
	private final Optional<IIdType> localUserId;
	private final Set<IIdType> nonLocalUserIds = FhirResourceComparison.idTypes().createSet() ;

	private void requireMatchingIdType( IIdType id ) {
		if ( !this.userResourceType.equalsIgnoreCase( id.getResourceType() ) )
			throw new IllegalArgumentException( "cannot form a \"" + this.userResourceType + "\" user identity with a " + id.getResourceType() + " id" ) ;
	}

	public UserIdentity( IIdType basisUserId, Optional<IIdType> localUserId, Collection<? extends IIdType> nonLocalUserIds ) {
		this.basisUserId = Objects.requireNonNull( basisUserId, "cannot build UserIdentity without basisUserId" );
		this.localUserId = localUserId;
		this.nonLocalUserIds.addAll(nonLocalUserIds) ;
		this.userResourceType = Objects.requireNonNull( basisUserId.getResourceType(), "cannot build UserIdentity when basisUserId is missing the resource type (\"" + basisUserId + "\")" );
		localUserId.ifPresent( this::requireMatchingIdType );
		nonLocalUserIds.forEach( this::requireMatchingIdType );
	}
	
	public String userResourceType() {
		return userResourceType;
	}

	public IIdType basisUserId() {
		return basisUserId;
	}

	public Optional<IIdType> localUserId() {
		return localUserId;
	}

	public Set<IIdType> nonLocalUserIds() {
		return nonLocalUserIds;
	}

	public UserIdentity withAdditionalNonLocalUserId(IIdType additionalNonLocalUserId) {
		Set<IIdType> expandedNonLocalUserIds = FhirResourceComparison.idTypes().createSet( nonLocalUserIds ) ;
		expandedNonLocalUserIds.add( additionalNonLocalUserId ) ;
		return new UserIdentity( basisUserId, localUserId, expandedNonLocalUserIds );
	}

	private String userIdToString( IIdType userId ) {
		String format ;
		if ( userId.hasBaseUrl() ) {
			format = "%3$s (%1$s)" ;
		} else {
			format = "%3$s" ;
		}
		return String.format( format, userId.getBaseUrl(), userId.getResourceType(), userId.getIdPart() ) ;
	}

	@Override
	public String toString() {
		String format ;
		if ( localUserId.isPresent() && !nonLocalUserIds.isEmpty() )
			format = "[%1$s] %2$s\nauthorized as %3$s\na.k.a. %4$s" ;
		else if ( localUserId.isPresent() )
			format = "[%1$s] %2$s\nauthorized as %3$s" ;
		else if ( nonLocalUserIds.isEmpty() )
			format = "[%1$s]\nauthorized as %3$s\na.k.a. %4$s" ;
		else
			format = "[%1$s]\nauthorized as %3$s" ;
		return String.format(
				format,
				userResourceType,
				localUserId.map(this::userIdToString).orElse("-none-"),
				basisUserId,
				nonLocalUserIds.stream().map( this::userIdToString ).collect( joining(", ", "[ ", " ]" ) )
			);
	}

}