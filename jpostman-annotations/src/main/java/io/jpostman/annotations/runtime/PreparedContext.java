package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.jpostman.Collection;
import io.jpostman.JPostman.Context;
import io.jpostman.annotations.JPostmanContext;

final class PreparedContext<C> {

	C context;
	final Context loaded;
	final Collection collection;
	final JPostmanContext contextAnnotation;
	final List<String> dataloadLocations;
	final Map<String, Map<String, String>> assertionRules;
	private final Object owner;
	private final Field field;
	private final List<Mirror> mirrors = new ArrayList<>();

	PreparedContext(C context, Context loaded) {
		this(context, loaded, null, Collections.emptyList(), Collections.emptyMap(), null, null);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation) {
		this(context, loaded, contextAnnotation, Collections.emptyList(), Collections.emptyMap(), null, null);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation, List<String> dataloadLocations) {
		this(context, loaded, contextAnnotation, dataloadLocations, Collections.emptyMap(), null, null);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation, List<String> dataloadLocations,
			Map<String, Map<String, String>> assertionRules) {
		this(context, loaded, contextAnnotation, dataloadLocations, assertionRules, null, null);
	}

	PreparedContext(C context, Context loaded, Object owner, Field field) {
		this(context, loaded, null, Collections.emptyList(), Collections.emptyMap(), owner, field);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation, Object owner, Field field) {
		this(context, loaded, contextAnnotation, Collections.emptyList(), Collections.emptyMap(), owner, field);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation, List<String> dataloadLocations,
			Object owner, Field field) {
		this(context, loaded, contextAnnotation, dataloadLocations, Collections.emptyMap(), owner, field);
	}

	PreparedContext(C context, Context loaded, JPostmanContext contextAnnotation, List<String> dataloadLocations,
			Map<String, Map<String, String>> assertionRules, Object owner, Field field) {
		this.context = context;
		this.loaded = loaded;
		this.collection = loaded == null ? null : loaded.getCollection();
		this.contextAnnotation = contextAnnotation;
		this.dataloadLocations = dataloadLocations == null ? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(dataloadLocations));
		this.assertionRules = immutableRules(assertionRules);
		this.owner = owner;
		this.field = field;
	}

	private Map<String, Map<String, String>> immutableRules(Map<String, Map<String, String>> rules) {
		if (rules == null || rules.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, Map<String, String>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : rules.entrySet()) {
			copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
		}
		return Collections.unmodifiableMap(copy);
	}

	void update(C context) {
		this.context = context;
		setField(owner, field, context);
		for (Mirror mirror : mirrors) {
			setField(mirror.owner, mirror.field, context);
		}
	}

	void addMirror(Object owner, Field field) {
		if (owner == null || field == null) {
			return;
		}
		mirrors.add(new Mirror(owner, field));
	}

	private void setField(Object owner, Field field, C context) {
		if (owner == null || field == null) {
			return;
		}
		try {
			field.setAccessible(true);
			field.set(owner, context);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to update @JPostmanTestContext field: " + field.getName(), e);
		}
	}

	private static final class Mirror {
		final Object owner;
		final Field field;

		Mirror(Object owner, Field field) {
			this.owner = owner;
			this.field = field;
		}
	}
}
