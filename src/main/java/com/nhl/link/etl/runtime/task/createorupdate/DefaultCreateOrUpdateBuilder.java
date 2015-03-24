package com.nhl.link.etl.runtime.task.createorupdate;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cayenne.DataObject;
import org.apache.cayenne.exp.Property;
import org.apache.cayenne.map.ObjAttribute;
import org.apache.cayenne.map.ObjEntity;

import com.nhl.link.etl.CreateOrUpdateBuilder;
import com.nhl.link.etl.EtlRuntimeException;
import com.nhl.link.etl.EtlTask;
import com.nhl.link.etl.load.LoadListener;
import com.nhl.link.etl.mapper.AttributeMapper;
import com.nhl.link.etl.mapper.IdMapper;
import com.nhl.link.etl.mapper.KeyAdapter;
import com.nhl.link.etl.mapper.Mapper;
import com.nhl.link.etl.mapper.MultiAttributeMapper;
import com.nhl.link.etl.mapper.SafeMapKeyMapper;
import com.nhl.link.etl.metadata.RelationshipInfo;
import com.nhl.link.etl.metadata.RelationshipType;
import com.nhl.link.etl.runtime.cayenne.ITargetCayenneService;
import com.nhl.link.etl.runtime.extract.IExtractorService;
import com.nhl.link.etl.runtime.listener.CreateOrUpdateListener;
import com.nhl.link.etl.runtime.listener.CreateOrUpdateListenerFactory;
import com.nhl.link.etl.runtime.mapper.IKeyAdapterFactory;
import com.nhl.link.etl.runtime.token.ITokenManager;

/**
 * A builder of an ETL task that matches source data with target data based on a
 * certain unique attribute on both sides.
 */
@SuppressWarnings("deprecation")
public class DefaultCreateOrUpdateBuilder<T extends DataObject> implements CreateOrUpdateBuilder<T> {

	private static final int DEFAULT_BATCH_SIZE = 500;

	private ITargetCayenneService targetCayenneService;
	private IExtractorService extractorService;
	private ITokenManager tokenManager;
	private IKeyAdapterFactory keyMapAdapterFactory;

	private Class<T> type;
	private String extractorName;
	private int batchSize;
	private List<RelationshipInfo> relationships;

	@Deprecated
	private List<LoadListener<T>> loadListeners;

	private Map<Class<? extends Annotation>, List<CreateOrUpdateListener>> stageListeners;

	private Mapper<T> mapper;
	private boolean byId;
	private List<String> keyAttributes;

	public DefaultCreateOrUpdateBuilder(Class<T> type, ITargetCayenneService targetCayenneService,
			IExtractorService extractorService, ITokenManager tokenManager, IKeyAdapterFactory keyMapAdapterFactory) {

		this.extractorService = extractorService;
		this.batchSize = DEFAULT_BATCH_SIZE;
		this.type = type;
		this.targetCayenneService = targetCayenneService;
		this.tokenManager = tokenManager;
		this.relationships = new ArrayList<>();
		this.keyMapAdapterFactory = keyMapAdapterFactory;

		this.stageListeners = new HashMap<>();

		// always add stats listener..
		stageListener(CreateOrUpdateStatsListener.instance());

		this.loadListeners = new ArrayList<>();
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> sourceExtractor(String extractorName) {
		this.extractorName = extractorName;
		return this;
	}

	@Deprecated
	@Override
	public CreateOrUpdateBuilder<T> withExtractor(String extractorName) {
		return sourceExtractor(extractorName);
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> matchBy(Mapper<T> mapper) {
		this.byId = false;
		this.mapper = mapper;
		this.keyAttributes = null;
		return this;
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> matchBy(String... keyAttributes) {
		this.byId = false;
		this.mapper = null;
		this.keyAttributes = Arrays.asList(keyAttributes);
		return this;
	}

	/**
	 * @since 1.1
	 */
	@Override
	public DefaultCreateOrUpdateBuilder<T> matchBy(Property<?>... matchAttributes) {

		// it will fail later on 'build'; TODO: should we do early argument
		// checking?
		if (matchAttributes == null) {
			return this;
		}
		String[] names = new String[matchAttributes.length];
		for (int i = 0; i < matchAttributes.length; i++) {
			names[i] = matchAttributes[i].getName();
		}

		return matchBy(names);
	}

	/**
	 * @since 1.1
	 */
	@Override
	public DefaultCreateOrUpdateBuilder<T> matchById(String idProperty) {
		this.byId = true;
		this.mapper = null;
		this.keyAttributes = Collections.singletonList(idProperty);
		return this;
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> batchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	@Deprecated
	@Override
	public CreateOrUpdateBuilder<T> withBatchSize(int batchSize) {
		return batchSize(batchSize);
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> withToOneRelationship(String name,
			Class<? extends DataObject> relatedObjType, String keyAttribute) {
		this.relationships.add(new RelationshipInfo(name, keyAttribute, RelationshipType.TO_ONE, relatedObjType));
		return this;
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> withToManyRelationship(String name,
			Class<? extends DataObject> relatedObjType, String keyAttribute) {
		this.relationships.add(new RelationshipInfo(name, keyAttribute, RelationshipType.TO_MANY, relatedObjType));
		return this;
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> withToOneRelationship(String name,
			Class<? extends DataObject> relatedObjType, String keyAttribute, String relationshipKeyAttribute) {
		this.relationships.add(new RelationshipInfo(name, keyAttribute, RelationshipType.TO_ONE, relatedObjType,
				relationshipKeyAttribute));
		return this;
	}

	@Override
	public DefaultCreateOrUpdateBuilder<T> withToManyRelationship(String name,
			Class<? extends DataObject> relatedObjType, String keyAttribute, String relationshipKeyAttribute) {
		this.relationships.add(new RelationshipInfo(name, keyAttribute, RelationshipType.TO_MANY, relatedObjType,
				relationshipKeyAttribute));
		return this;
	}

	@Deprecated
	@Override
	public CreateOrUpdateBuilder<T> withListener(LoadListener<T> listener) {
		this.loadListeners.add(listener);
		return this;
	}

	@Override
	public CreateOrUpdateBuilder<T> stageListener(Object listener) {
		CreateOrUpdateListenerFactory.appendListeners(stageListeners, listener);
		return this;
	}

	private String getSingleMatchAttribute() {
		if (keyAttributes == null || keyAttributes.isEmpty()) {
			return null;
		}
		if (keyAttributes.size() > 1) {
			throw new IllegalStateException("Trying to get a single match attribute but multi key matching is set");
		}
		return keyAttributes.get(0);
	}

	private ObjAttribute getMatchAttribute() {

		ObjEntity entity = targetCayenneService.entityResolver().getObjEntity(type);

		if (entity == null) {
			throw new IllegalStateException("Type " + type.getName() + " is not mapped in Cayenne");
		}

		if (byId) {
			return entity.getPrimaryKeys().iterator().next();
		} else {
			String matchAttribute = getSingleMatchAttribute();
			ObjAttribute a = entity.getAttribute(matchAttribute);
			if (a == null) {
				throw new IllegalStateException("Invalid attribute name " + matchAttribute + " for entity "
						+ entity.getName());
			}
			return a;
		}
	}

	@Override
	public EtlTask task() throws IllegalStateException {

		if (extractorName == null) {
			throw new IllegalStateException("Required 'extractorName' is not set");
		}

		return new CreateOrUpdateTask<T>(extractorName, batchSize, targetCayenneService, extractorService,
				tokenManager, createProcessor());
	}

	private CreateOrUpdateSegmentProcessor<T> createProcessor() {

		Mapper<T> mapper = createMapper();
		CreateOrUpdateStrategy<T> createOrUpdateStrategy = createCreateOrUpdateStrategy();

		SourceMapper<T> sourceMapper = new SourceMapper<>(mapper);
		TargetMatcher<T> targetMatcher = new TargetMatcher<>(type, mapper);
		CreateOrUpdateMerger<T> merger = new CreateOrUpdateMerger<>(type, mapper, createOrUpdateStrategy);

		return new CreateOrUpdateSegmentProcessor<>(RowConverter.instance(), sourceMapper, targetMatcher, merger,
				stageListeners, loadListeners);
	}

	private CreateOrUpdateStrategy<T> createCreateOrUpdateStrategy() {
		if (byId) {
			return new CayenneCreateOrUpdateWithPKStrategy<>(relationships, getSingleMatchAttribute());
		} else {
			return new CayenneCreateOrUpdateStrategy<>(relationships);
		}
	}

	private Mapper<T> createMapper() {

		// not wrapping custom matcher, presuming the user knows what's he's
		// doing and his matcher generates proper keys
		if (this.mapper != null) {
			return this.mapper;
		}

		if (keyAttributes == null) {
			throw new IllegalStateException("'matcher' or 'matchAttribute' must be set");
		}

		Mapper<T> matcher;

		if (byId) {
			matcher = new IdMapper<>(pkAttribute(), getSingleMatchAttribute());
		} else if (keyAttributes.size() > 1) {
			matcher = new MultiAttributeMapper<>(keyAttributes);
		} else {
			matcher = new AttributeMapper<>(getSingleMatchAttribute());
		}

		KeyAdapter keyAdapter;

		// TODO: mapping keyMapAdapters by type doesn't take into account
		// composition and hierarchy of the keys ... need a different approach.
		// for now resorting to the hacks below
		if (keyAttributes.size() > 1) {
			keyAdapter = keyMapAdapterFactory.adapter(List.class);
		} else {
			ObjAttribute attribute = getMatchAttribute();
			keyAdapter = keyMapAdapterFactory.adapter(attribute.getJavaClass());
		}

		return new SafeMapKeyMapper<>(matcher, keyAdapter);
	}

	private String pkAttribute() {
		ObjEntity oe = targetCayenneService.entityResolver().getObjEntity(type);
		if (oe == null) {
			throw new EtlRuntimeException("Java class " + type.getName() + " is not mapped in Cayenne");
		}

		Collection<String> pks = oe.getPrimaryKeyNames();
		if (pks.size() != 1) {
			throw new EtlRuntimeException("Only single-column PK is supported for now. Got " + pks.size()
					+ " for entity: " + oe.getName());
		}

		return pks.iterator().next();
	}

}