package edu.ohsu.cmp.ecp.sds;

import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.dao.data.IPartitionDao;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.jpa.model.entity.StorageSettings;
import ca.uhn.fhir.rest.api.server.RequestDetails;

@Component
public class SupplementalDataStorePartition {

	@Inject
	SupplementalDataStoreProperties sdsProperties;

	@Inject
	IPartitionDao daoPartition;

	@Inject
	StorageSettings storageSettings;
	
    @Inject
    @Named("transactionManager")
    protected PlatformTransactionManager txManager;
    
    private void doInTransaction( Runnable task ) {
    	TransactionTemplate tmpl = new TransactionTemplate(txManager);
    	
        tmpl.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
            	task.run() ;
            }
        });
    }
    
    private <T> T doInTransaction( Supplier<T> task ) {
    	TransactionTemplate tmpl = new TransactionTemplate(txManager);
    	
    	return tmpl.execute(new TransactionCallback<T>() {
    		@Override
    		public T doInTransaction(TransactionStatus status) {
    			return task.get() ;
    		}
    	});
    }
    
	public boolean userIsLocal( IIdType userId ) {
		if ( !userId.hasBaseUrl() )
			return true ;
		if ( sdsProperties.getPartition().getLocalName().equals( userId.getBaseUrl() ) )
			return true ;
		if ( storageSettings.getTreatBaseUrlsAsLocal().contains( userId.getBaseUrl() ) )
			return true ;
		return false ;
	}
	
	public boolean userIsNonLocal( IIdType userId ) {
		return !userIsLocal( userId ) ;
	}
	
	public RequestPartitionId partitionIdFromRequest(RequestDetails theRequestDetails) {
		final String partitionName = partitionNameFromRequest(theRequestDetails);
		return RequestPartitionId.fromPartitionName(partitionName);
	}

	protected String partitionNameFromRequest(RequestDetails theRequestDetails) {
		final String httpHeader = sdsProperties.getPartition().getHttpHeader();
		final String partitionNameHeaderValue = theRequestDetails.getHeader( httpHeader );
		if (StringUtils.isNotBlank(partitionNameHeaderValue)) {
			/*
			 * later we want to recreate the partition name
			 *   from a fully-qualified resource id
			 * ---
			 * an id's urlBase has no trailing slash when
			 *   interpreted from a string via new IdType( String )
			 * so here we strip any trailing slash from
			 *   the partition name found in the request
			 */
			return partitionNameHeaderValue.replaceFirst("/$", "");
		} else {
			return sdsProperties.getPartition().getLocalName();
		}
	}

	public void establishLocalPartition() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		doInTransaction( () -> {
			if (!daoPartition.findForName(localPartitionName).isPresent()) {
				daoPartition.save(newLocalPartitionEntity());
			}
		});
	}

	public void establishNonLocalPartition( String partitionName ) {
		doInTransaction( () -> {
			if (!daoPartition.findForName(partitionName).isPresent()) {
				daoPartition.save(newNonLocalPartitionEntity(partitionName));
			}
		});
	}

	public boolean partitionExists( String partitionName ) {
		return doInTransaction( () -> {
			return daoPartition.findForName(partitionName).isPresent() ;
		});
	}

	private int generatePartitionId(String partitionName) {
		return partitionName.hashCode();
	}

	private PartitionEntity newLocalPartitionEntity() {
		String localPartitionName = sdsProperties.getPartition().getLocalName();

		final PartitionEntity partitionEntity = new PartitionEntity();
		partitionEntity.setId(generatePartitionId(localPartitionName));
		partitionEntity.setName(localPartitionName);
		partitionEntity.setDescription("groups new resources not originating elsewhere");
		return partitionEntity;
	}

	private PartitionEntity newNonLocalPartitionEntity(String nonLocalPartitionName) {
		final PartitionEntity partitionEntity = new PartitionEntity();
		partitionEntity.setId(generatePartitionId(nonLocalPartitionName));
		partitionEntity.setName(nonLocalPartitionName);
		partitionEntity.setDescription(String.format("groups resources originating from \"%1$s\"", nonLocalPartitionName));
		return partitionEntity;
	}

}
