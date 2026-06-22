package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;

import io.jpostman.Collection;
import io.jpostman.JPostman.Context;

final class PreparedContext<C> {

	C context;
	final Context loaded;
	final Collection collection;
	private final Object owner;
	private final Field field;

	PreparedContext(C context, Context loaded) {
		this(context, loaded, null, null);
	}

	PreparedContext(C context, Context loaded, Object owner, Field field) {
		this.context = context;
		this.loaded = loaded;
		this.collection = loaded == null ? null : loaded.getCollection();
		this.owner = owner;
		this.field = field;
	}

	void update(C context) {
		this.context = context;
		if (owner == null || field == null) {
			return;
		}
		try {
			field.set(owner, context);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to update @JPostmanTestContext field: " + field.getName(), e);
		}
	}
}
