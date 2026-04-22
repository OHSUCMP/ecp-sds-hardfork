package edu.ohsu.cmp.ecp.sds;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;

public interface AncillaryResources {
	List<Class<? extends IBaseResource>> getAncillaryResourceClasses();
	boolean isAncillaryResource(IBaseResource resource);
}
