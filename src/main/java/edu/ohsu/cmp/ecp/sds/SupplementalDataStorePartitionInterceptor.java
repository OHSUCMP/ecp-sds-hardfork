package edu.ohsu.cmp.ecp.sds;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.partition.IRequestPartitionHelperSvc;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

@Interceptor
@Component
public class SupplementalDataStorePartitionInterceptor {

	@Inject
	SupplementalDataStorePartition partition;

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IRequestPartitionHelperSvc requestPartitionHelperSvc;
	
	@PostConstruct
	public void establishLocalPartition() {
		partition.establishLocalPartition() ;
	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_CREATE)
	public RequestPartitionId partitionIdentifyCreate(RequestDetails theRequestDetails) {
		if ( !requestPartitionHelperSvc.isResourcePartitionable(theRequestDetails.getResourceName()) )
			return RequestPartitionId.defaultPartition() ;

		RequestPartitionId partitionId = partition.partitionIdFromRequest( theRequestDetails ) ;

		validateAndEstablishNamedPartition( partitionId, theRequestDetails ) ;

		return partitionId;
	}

	protected void validateAndEstablishNamedPartition( RequestPartitionId partitionId, RequestDetails theRequestDetails ) {

		if ( partitionId.hasPartitionNames() ) {
			for ( String partitionName : partitionId.getPartitionNames() ) {

				validateResourceBelongsInPartition(theRequestDetails.getResource(), partitionName);

				if (!sdsProperties.getPartition().getLocalName().equals(partitionName)) {
					partition.establishNonLocalPartition( partitionName ) ;
				}
			}
		}
	}

	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId partitionIdentifyRead(RequestDetails theRequestDetails) {
		if ( !requestPartitionHelperSvc.isResourcePartitionable(theRequestDetails.getResourceName()) )
			return RequestPartitionId.defaultPartition() ;

		/*
		 * widen the request partition to allow searching all partitions
		 * for resources to delete during _cascade=delete
		 */
		if ( RequestTypeEnum.DELETE == theRequestDetails.getRequestType() ) {
			return RequestPartitionId.allPartitions() ;
		}

		return partition.partitionIdFromRequest( theRequestDetails ) ;
	}


	public void validateResourceBelongsInPartition(IBaseResource resource, String partitionName) throws InvalidRequestException {
		if (null == resource)
			return;

		IIdType id = resource.getIdElement();
		if (id == null || StringUtils.isBlank(id.getIdPart())) {
			// create (PROs, etc.)

			if ( ! StringUtils.equals(sdsProperties.getPartition().getLocalName(), partitionName) ) {
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in local SDS partition into SDS partition \"%1$s\"", partitionName));
			}

		} else {
			// clone from foreign, or update existing

			if (sdsProperties.getRequireBaseUrl() && ! id.hasBaseUrl()) {
				throw new InvalidRequestException("id element present without base url; cannot match SDS partition");

			} else if (id.hasBaseUrl() && ! StringUtils.equals(id.getBaseUrl(), partitionName)) {
				throw new InvalidRequestException(String.format("cannot store resource identified as belonging in SDS partition \"%1$s\" into SDS partition \"%2$s\"", id.getBaseUrl(), partitionName));
			}
		}
	}

}
