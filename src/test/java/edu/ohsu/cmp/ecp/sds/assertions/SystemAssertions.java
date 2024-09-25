package edu.ohsu.cmp.ecp.sds.assertions;

import edu.ohsu.cmp.ecp.sds.util.SdsSystemOperations;

public class SystemAssertions {
	private final SdsSystemOperations sdsSystemOps ;

	public SystemAssertions( SdsSystemOperations sdsSystemOps ) {
		this.sdsSystemOps = sdsSystemOps ;
	}

	public SdsSystemOperations operations() {
		return sdsSystemOps;
	}

}