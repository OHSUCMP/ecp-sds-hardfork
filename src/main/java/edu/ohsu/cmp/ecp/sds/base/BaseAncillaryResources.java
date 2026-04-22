package edu.ohsu.cmp.ecp.sds.base;

import edu.ohsu.cmp.ecp.sds.AncillaryResources;
import org.hl7.fhir.instance.model.api.IBaseResource;

public abstract class BaseAncillaryResources implements AncillaryResources {
	@Override
	public boolean isAncillaryResource(IBaseResource resource) {
		if (resource == null) return false;
		return getAncillaryResourceClasses().contains(resource.getClass());
	}
}
