package edu.ohsu.cmp.ecp.sds.assertions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.BiPredicate;

public class UpdateableOnce<T> {

	private final String description ;
	private Optional<T> value ;
	private BiPredicate<T,T> equalityChecker ;

	public UpdateableOnce() {
		this( UpdateableOnce.class.getSimpleName() ) ;
	}
	
	public UpdateableOnce( String description ) {
		this( description, Optional.empty() ) ;
	}

	public UpdateableOnce( String description, BiPredicate<T,T> equalityChecker ) {
		this( description, Optional.empty(), equalityChecker ) ;
	}
	
	public UpdateableOnce( Optional<T> value ) {
		this( UpdateableOnce.class.getSimpleName(), Optional.empty() ) ;
	}
	
	public UpdateableOnce( String description, Optional<T> value ) {
		this( UpdateableOnce.class.getSimpleName(), Optional.empty(), defaultEqualityChecker() ) ;
	}
	
	public UpdateableOnce( String description, Optional<T> value, BiPredicate<T,T> equalityChecker ) {
		this.value = value ;
		this.description = description ;
		this.equalityChecker = equalityChecker ;
	}

	public Optional<T> value() { return value ; }

	public T update( T value ) {
		if ( null == value )
			throw new IllegalArgumentException( description + " cannot be updated to be null" ) ;
		if ( this.value.isPresent() ) {
			if ( !equalityChecker.test( this.value.get(), value ) )
				throw new IllegalStateException( description + " cannot be updated because it already has a value" ) ;
		} else {
			this.value = Optional.of( value );
		}
		return this.value.get() ;
	}

	public <X extends Throwable> T orElseThrow( Class<? extends X> exceptionClass ) throws X {
		return this.value
			.orElseThrow( () -> makeException(exceptionClass) )
			;
	}

	private <X extends Throwable> X makeException( Class<? extends X> exceptionClass ) {
		try {
			Constructor<? extends X> ctor = exceptionClass.getDeclaredConstructor( String.class );
			X instance = ctor.newInstance( description + " has never been updated with a non-null value" );
			return instance ;
		} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
			throw new IllegalArgumentException( "failed to construct an instance of " + exceptionClass.getName(), ex ) ;
		}
	}
	
	private static <T> BiPredicate<T,T> defaultEqualityChecker() {
		return (BiPredicate<T,T>)DEFAULT_EQUALITY_CHECKER ;
	}
	
	private static BiPredicate<?,?> DEFAULT_EQUALITY_CHECKER = (a,b) -> {
		return null == a ? a == b : a.equals( b ) ;
	} ;
}
