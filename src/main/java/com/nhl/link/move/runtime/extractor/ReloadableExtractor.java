package com.nhl.link.move.runtime.extractor;

import java.util.Map;

import com.nhl.link.move.RowReader;
import com.nhl.link.move.extractor.Extractor;
import com.nhl.link.move.extractor.model.ExtractorModel;
import com.nhl.link.move.extractor.model.ExtractorName;
import com.nhl.link.move.runtime.extractor.model.IExtractorModelService;

/**
 * An extractor decorator that can recreate the underlying {@link Extractor} if
 * the configuration object has changed underneath.
 */
public class ReloadableExtractor implements Extractor {

	private IExtractorModelService extractorModelService;
	private Map<String, IExtractorFactory> factories;
	private ExtractorName name;

	private long lastSeen;
	private Extractor delegate;

	public ReloadableExtractor(IExtractorModelService extractorModelService, Map<String, IExtractorFactory> factories,
			ExtractorName name) {

		this.extractorModelService = extractorModelService;
		this.factories = factories;
		this.name = name;
	}

	@Override
	public RowReader getReader(Map<String, ?> parameters) {
		return getDelegate().getReader(parameters);
	}

	protected Extractor getDelegate() {

		ExtractorModel model = extractorModelService.get(name);

		if (needsReload(model)) {

			synchronized (this) {
				if (needsReload(model)) {

					this.lastSeen = model.getLoadedOn() + 1;

					IExtractorFactory factory = factories.get(model.getType());
					if (factory == null) {
						throw new IllegalStateException("No factory mapped for Extractor type of '" + model.getType()
								+ "'");
					}

					this.delegate = factory.createExtractor(model);
				}
			}
		}

		return delegate;
	}

	boolean needsReload(ExtractorModel model) {
		return delegate == null || model.getLoadedOn() > lastSeen;
	}
}
