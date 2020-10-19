/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.persistence.local.room;

import static com.google.android.gnd.util.ImmutableListCollector.toImmutableList;
import static com.google.android.gnd.util.ImmutableSetCollector.toImmutableSet;
import static java8.util.stream.StreamSupport.stream;

import androidx.annotation.NonNull;
import androidx.room.Transaction;
import com.google.android.gnd.model.AuditInfo;
import com.google.android.gnd.model.Mutation;
import com.google.android.gnd.model.Mutation.Type;
import com.google.android.gnd.model.Project;
import com.google.android.gnd.model.User;
import com.google.android.gnd.model.basemap.OfflineBaseMap;
import com.google.android.gnd.model.basemap.tile.TileSource;
import com.google.android.gnd.model.feature.Feature;
import com.google.android.gnd.model.feature.FeatureMutation;
import com.google.android.gnd.model.form.Element;
import com.google.android.gnd.model.form.Field;
import com.google.android.gnd.model.form.Form;
import com.google.android.gnd.model.form.MultipleChoice;
import com.google.android.gnd.model.form.Option;
import com.google.android.gnd.model.layer.Layer;
import com.google.android.gnd.model.observation.Observation;
import com.google.android.gnd.model.observation.ObservationMutation;
import com.google.android.gnd.persistence.local.LocalDataStore;
import com.google.android.gnd.persistence.local.room.dao.FeatureDao;
import com.google.android.gnd.persistence.local.room.dao.FeatureMutationDao;
import com.google.android.gnd.persistence.local.room.dao.FieldDao;
import com.google.android.gnd.persistence.local.room.dao.FormDao;
import com.google.android.gnd.persistence.local.room.dao.LayerDao;
import com.google.android.gnd.persistence.local.room.dao.MultipleChoiceDao;
import com.google.android.gnd.persistence.local.room.dao.ObservationDao;
import com.google.android.gnd.persistence.local.room.dao.ObservationMutationDao;
import com.google.android.gnd.persistence.local.room.dao.OfflineBaseMapDao;
import com.google.android.gnd.persistence.local.room.dao.OfflineBaseMapSourceDao;
import com.google.android.gnd.persistence.local.room.dao.OptionDao;
import com.google.android.gnd.persistence.local.room.dao.ProjectDao;
import com.google.android.gnd.persistence.local.room.dao.TileSourceDao;
import com.google.android.gnd.persistence.local.room.dao.UserDao;
import com.google.android.gnd.persistence.local.room.entity.AuditInfoEntity;
import com.google.android.gnd.persistence.local.room.entity.FeatureEntity;
import com.google.android.gnd.persistence.local.room.entity.FeatureMutationEntity;
import com.google.android.gnd.persistence.local.room.entity.FieldEntity;
import com.google.android.gnd.persistence.local.room.entity.FormEntity;
import com.google.android.gnd.persistence.local.room.entity.LayerEntity;
import com.google.android.gnd.persistence.local.room.entity.MultipleChoiceEntity;
import com.google.android.gnd.persistence.local.room.entity.ObservationEntity;
import com.google.android.gnd.persistence.local.room.entity.ObservationMutationEntity;
import com.google.android.gnd.persistence.local.room.entity.OfflineBaseMapEntity;
import com.google.android.gnd.persistence.local.room.entity.OfflineBaseMapSourceEntity;
import com.google.android.gnd.persistence.local.room.entity.OptionEntity;
import com.google.android.gnd.persistence.local.room.entity.ProjectEntity;
import com.google.android.gnd.persistence.local.room.entity.TileSourceEntity;
import com.google.android.gnd.persistence.local.room.entity.UserEntity;
import com.google.android.gnd.persistence.local.room.models.EntityState;
import com.google.android.gnd.persistence.local.room.models.TileEntityState;
import com.google.android.gnd.persistence.local.room.models.UserDetails;
import com.google.android.gnd.rx.Schedulers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import timber.log.Timber;

/**
 * Implementation of local data store using Room ORM. Room abstracts persistence between a local db
 * and Java objects using a mix of inferred mappings based on Java field names and types, and custom
 * annotations. Mappings are defined through the various Entity objects in the package and related
 * embedded classes.
 */
@Singleton
public class RoomLocalDataStore implements LocalDataStore {

  @Inject OptionDao optionDao;
  @Inject MultipleChoiceDao multipleChoiceDao;
  @Inject FieldDao fieldDao;
  @Inject FormDao formDao;
  @Inject LayerDao layerDao;
  @Inject ProjectDao projectDao;
  @Inject FeatureDao featureDao;
  @Inject FeatureMutationDao featureMutationDao;
  @Inject ObservationDao observationDao;
  @Inject ObservationMutationDao observationMutationDao;
  @Inject TileSourceDao tileSourceDao;
  @Inject UserDao userDao;
  @Inject
  OfflineBaseMapDao offlineBaseMapDao;
  @Inject OfflineBaseMapSourceDao offlineBaseMapSourceDao;
  @Inject Schedulers schedulers;

  @Inject
  RoomLocalDataStore() {}

  @NonNull
  private Completable insertOrUpdateOption(String fieldId, @NonNull Option option) {
    return optionDao
        .insertOrUpdate(OptionEntity.fromOption(fieldId, option))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable insertOrUpdateOptions(String fieldId, @NonNull ImmutableList<Option> options) {
    return Observable.fromIterable(options)
        .flatMapCompletable(option -> insertOrUpdateOption(fieldId, option))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable insertOrUpdateMultipleChoice(String fieldId, @NonNull MultipleChoice multipleChoice) {
    return multipleChoiceDao
        .insertOrUpdate(MultipleChoiceEntity.fromMultipleChoice(fieldId, multipleChoice))
        .andThen(insertOrUpdateOptions(fieldId, multipleChoice.getOptions()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable insertOrUpdateField(String formId, Element.Type elementType, @NonNull Field field) {
    return fieldDao
        .insertOrUpdate(FieldEntity.fromField(formId, elementType, field))
        .andThen(
            Observable.just(field)
                .filter(__ -> field.getMultipleChoice() != null)
                .flatMapCompletable(
                    __ -> insertOrUpdateMultipleChoice(field.getId(), field.getMultipleChoice())))
        .subscribeOn(schedulers.io());
  }

  private Completable insertOrUpdateElements(String formId, @NonNull ImmutableList<Element> elements) {
    return Observable.fromIterable(elements)
        .flatMapCompletable(
            element -> insertOrUpdateField(formId, element.getType(), element.getField()));
  }

  @NonNull
  private Completable insertOrUpdateForm(String layerId, @NonNull Form form) {
    return formDao
        .insertOrUpdate(FormEntity.fromForm(layerId, form))
        .andThen(insertOrUpdateElements(form.getId(), form.getElements()))
        .subscribeOn(schedulers.io());
  }

  private Completable insertOrUpdateForms(String layerId, @NonNull List<Form> forms) {
    return Observable.fromIterable(forms)
        .flatMapCompletable(form -> insertOrUpdateForm(layerId, form));
  }

  @NonNull
  private Completable insertOrUpdateLayer(String projectId, @NonNull Layer layer) {
    return layerDao
        .insertOrUpdate(LayerEntity.fromLayer(projectId, layer))
        .andThen(
            insertOrUpdateForms(
                layer.getId(), layer.getForm().map(Arrays::asList).orElseGet(ArrayList::new)))
        .subscribeOn(schedulers.io());
  }

  private Completable insertOrUpdateLayers(String projectId, @NonNull List<Layer> layers) {
    return Observable.fromIterable(layers)
        .flatMapCompletable(layer -> insertOrUpdateLayer(projectId, layer));
  }

  private Completable insertOfflineBaseMapSources(@NonNull Project project) {
    return Observable.fromIterable(project.getOfflineBaseMapSources())
        .flatMapCompletable(
            source ->
                offlineBaseMapSourceDao.insert(
                    OfflineBaseMapSourceEntity.fromModel(project.getId(), source)));
  }

  @NonNull
  @Transaction
  @Override
  public Completable insertOrUpdateProject(@NonNull Project project) {
    return projectDao
        .insertOrUpdate(ProjectEntity.fromProject(project))
        .andThen(layerDao.deleteByProjectId(project.getId()))
        .andThen(insertOrUpdateLayers(project.getId(), project.getLayers()))
        .andThen(offlineBaseMapSourceDao.deleteByProjectId(project.getId()))
        .andThen(insertOfflineBaseMapSources(project))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable insertOrUpdateUser(@NonNull User user) {
    return userDao.insertOrUpdate(UserEntity.fromUser(user)).subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<User> getUser(String id) {
    return userDao
        .findById(id)
        .doOnError(e -> Timber.e(e, "Error loading user from local db: %s", id))
        // Fail with NoSuchElementException if not found.
        .toSingle()
        .map(UserEntity::toUser)
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<ImmutableList<Project>> getProjects() {
    return projectDao
        .getAllProjects()
        .map(list -> stream(list).map(ProjectEntity::toProject).collect(toImmutableList()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Maybe<Project> getProjectById(String id) {
    return projectDao.getProjectById(id).map(ProjectEntity::toProject).subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable deleteProject(@NonNull Project project) {
    return projectDao.delete(ProjectEntity.fromProject(project)).subscribeOn(schedulers.io());
  }

  @Transaction
  @Override
  public Completable applyAndEnqueue(@NonNull FeatureMutation mutation) {
    try {
      return apply(mutation).andThen(enqueue(mutation));
    } catch (LocalDataStoreException e) {
      return Completable.error(e);
    }
  }

  // TODO(#127): Decouple from Project and pass in project id instead.
  @NonNull
  @Override
  public Flowable<ImmutableSet<Feature>> getFeaturesOnceAndStream(@NonNull Project project) {
    return featureDao
        .findOnceAndStream(project.getId(), EntityState.DEFAULT)
        .map(
            list ->
                stream(list)
                    .map(f -> FeatureEntity.toFeature(f, project))
                    .collect(toImmutableSet()))
        .subscribeOn(schedulers.io());
  }

  // TODO(#127): Decouple from Project and remove project from args.
  @NonNull
  @Override
  public Maybe<Feature> getFeature(@NonNull Project project, String featureId) {
    return featureDao
        .findById(featureId)
        .map(f -> FeatureEntity.toFeature(f, project))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Maybe<Observation> getObservation(@NonNull Feature feature, String observationId) {
    return observationDao
        .findById(observationId)
        .map(obs -> ObservationEntity.toObservation(feature, obs))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<ImmutableList<Observation>> getObservations(@NonNull Feature feature, String formId) {
    return observationDao
        .findByFeatureId(feature.getId(), formId, EntityState.DEFAULT)
        .map(
            list ->
                stream(list)
                    .map(obs -> ObservationEntity.toObservation(feature, obs))
                    .collect(toImmutableList()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Flowable<ImmutableSet<TileSource>> getTileSourcesOnceAndStream() {
    return tileSourceDao
        .findAllOnceAndStream()
        .map(list -> stream(list).map(TileSourceEntity::toTileSource).collect(toImmutableSet()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<ImmutableList<Mutation>> getPendingMutations(String featureId) {
    return featureMutationDao
        .findByFeatureId(featureId)
        .flattenAsObservable(fms -> fms)
        .map(FeatureMutationEntity::toMutation)
        .cast(Mutation.class)
        .mergeWith(
            observationMutationDao
                .findByFeatureId(featureId)
                .flattenAsObservable(oms -> oms)
                .map(ObservationMutationEntity::toMutation)
                .cast(Mutation.class))
        .toList()
        .map(ImmutableList::copyOf)
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Transaction
  @Override
  public Completable updateMutations(@NonNull ImmutableList<Mutation> mutations) {
    return featureMutationDao
        .updateAll(toFeatureMutationEntities(mutations))
        .andThen(
            observationMutationDao
                .updateAll(toObservationMutationEntities(mutations))
                .subscribeOn(schedulers.io()))
        .subscribeOn(schedulers.io());
  }

  private ImmutableList<ObservationMutationEntity> toObservationMutationEntities(
      @NonNull ImmutableList<Mutation> mutations) {
    return stream(ObservationMutation.filter(mutations))
        .map(ObservationMutationEntity::fromMutation)
        .collect(toImmutableList());
  }

  private ImmutableList<FeatureMutationEntity> toFeatureMutationEntities(
      @NonNull ImmutableList<Mutation> mutations) {
    return stream(FeatureMutation.filter(mutations))
        .map(FeatureMutationEntity::fromMutation)
        .collect(toImmutableList());
  }

  @Override
  public Completable finalizePendingMutations(@NonNull ImmutableList<Mutation> mutations) {
    return finalizeDeletions(mutations).andThen(removePending(mutations));
  }

  private Completable finalizeDeletions(@NonNull ImmutableList<Mutation> mutations) {
    return Observable.fromIterable(mutations)
        .filter(mutation -> mutation.getType() == Type.DELETE)
        .flatMapCompletable(
            mutation -> {
              if (mutation instanceof ObservationMutation) {
                return deleteObservation(((ObservationMutation) mutation).getObservationId());
              } else if (mutation instanceof FeatureMutation) {
                return deleteFeature(mutation.getFeatureId());
              } else {
                return Completable.error(new RuntimeException("Unknown type : " + mutation));
              }
            });
  }

  @NonNull
  private Completable removePending(@NonNull ImmutableList<Mutation> mutations) {
    return featureMutationDao
        .deleteAll(FeatureMutation.ids(mutations))
        .andThen(
            observationMutationDao
                .deleteAll(ObservationMutation.ids(mutations))
                .subscribeOn(schedulers.io()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Transaction
  @Override
  public Completable mergeFeature(@NonNull Feature feature) {
    // TODO(#109): Once we user can edit feature locally, apply pending mutations before saving.
    return featureDao
        .insertOrUpdate(FeatureEntity.fromFeature(feature))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Transaction
  @Override
  public Completable mergeObservation(@NonNull Observation observation) {
    ObservationEntity observationEntity = ObservationEntity.fromObservation(observation);
    return observationMutationDao
        .findByObservationId(observation.getId())
        .flatMapCompletable(mutations -> mergeObservation(observationEntity, mutations))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable mergeObservation(
      @NonNull ObservationEntity observation, @NonNull List<ObservationMutationEntity> mutations) {
    if (mutations.isEmpty()) {
      return observationDao.insertOrUpdate(observation);
    }
    ObservationMutationEntity lastMutation = mutations.get(mutations.size() - 1);
    return getUser(lastMutation.getUserId())
        .map(user -> applyMutations(observation, mutations, user))
        .flatMapCompletable(obs -> observationDao.insertOrUpdate(obs));
  }

  @NonNull
  private ObservationEntity applyMutations(
      @NonNull ObservationEntity observation, @NonNull List<ObservationMutationEntity> mutations, @NonNull User user) {
    ObservationMutationEntity lastMutation = mutations.get(mutations.size() - 1);
    long clientTimestamp = lastMutation.getClientTimestamp();
    Timber.v("Merging observation " + this + " with mutations " + mutations);
    ObservationEntity.Builder builder = observation.toBuilder();
    // Merge changes to responses.
    for (ObservationMutationEntity mutation : mutations) {
      builder.applyMutation(mutation);
    }
    // Update modified user and time.
    AuditInfoEntity lastModified =
        AuditInfoEntity.builder()
            .setUser(UserDetails.fromUser(user))
            .setClientTimestamp(clientTimestamp)
            .build();
    builder.setLastModified(lastModified);
    Timber.v("Merged observation %s", builder.build());
    return builder.build();
  }

  @NonNull
  private Completable apply(@NonNull FeatureMutation mutation) throws LocalDataStoreException {
    switch (mutation.getType()) {
      case CREATE:
      case UPDATE:
        return getUser(mutation.getUserId())
            .flatMapCompletable(user -> insertOrUpdateFeature(mutation, user));
      case DELETE:
        return featureDao
            .findById(mutation.getFeatureId())
            .flatMapCompletable(entity -> markFeatureForDeletion(entity, mutation))
            .subscribeOn(schedulers.io());
      default:
        throw LocalDataStoreException.unknownMutationType(mutation.getType());
    }
  }

  private Completable markFeatureForDeletion(@NonNull FeatureEntity entity, FeatureMutation mutation) {
    return featureDao
        .update(entity.toBuilder().setState(EntityState.DELETED).build())
        .doOnSubscribe(__ -> Timber.d("Marking feature as deleted : %s", mutation))
        .ignoreElement();
  }

  @NonNull
  private Completable insertOrUpdateFeature(@NonNull FeatureMutation mutation, User user) {
    return featureDao
        .insertOrUpdate(FeatureEntity.fromMutation(mutation, AuditInfo.now(user)))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable deleteFeature(String featureId) {
    return featureDao
        .findById(featureId)
        .toSingle()
        .doOnSubscribe(__ -> Timber.d("Deleting local feature : %s", featureId))
        .flatMapCompletable(entity -> featureDao.delete(entity))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable enqueue(@NonNull FeatureMutation mutation) {
    return featureMutationDao
        .insert(FeatureMutationEntity.fromMutation(mutation))
        .subscribeOn(schedulers.io());
  }

  @Transaction
  @Override
  public Completable applyAndEnqueue(@NonNull ObservationMutation mutation) {
    try {
      return apply(mutation).andThen(enqueue(mutation));
    } catch (LocalDataStoreException e) {
      return Completable.error(e);
    }
  }

  /**
   * Applies mutation to observation in database or creates a new one.
   *
   * @return A Completable that emits an error if mutation type is "UPDATE" but entity does not *
   *     exist, or if type is "CREATE" and entity already exists.
   */
  @NonNull
  public Completable apply(@NonNull ObservationMutation mutation) throws LocalDataStoreException {
    switch (mutation.getType()) {
      case CREATE:
        return getUser(mutation.getUserId())
            .flatMapCompletable(user -> createObservation(mutation, user));
      case UPDATE:
        return getUser(mutation.getUserId())
            .flatMapCompletable(user -> updateObservation(mutation, user));
      case DELETE:
        return observationDao
            .findById(mutation.getObservationId())
            .flatMapCompletable(entity -> markObservationForDeletion(entity, mutation));
      default:
        throw LocalDataStoreException.unknownMutationType(mutation.getType());
    }
  }

  @NonNull
  private Completable createObservation(@NonNull ObservationMutation mutation, User user) {
    return observationDao
        .insert(ObservationEntity.fromMutation(mutation, AuditInfo.now(user)))
        .doOnSubscribe(__ -> Timber.v("Inserting observation: %s", mutation))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable updateObservation(@NonNull ObservationMutation mutation, @NonNull User user) {
    ObservationMutationEntity mutationEntity = ObservationMutationEntity.fromMutation(mutation);
    return observationDao
        .findById(mutation.getObservationId())
        .doOnSubscribe(__ -> Timber.v("Applying mutation: %s", mutation))
        // Emit NoSuchElementException if not found.
        .toSingle()
        .map(obs -> applyMutations(obs, ImmutableList.of(mutationEntity), user))
        .flatMapCompletable(obs -> observationDao.insertOrUpdate(obs).subscribeOn(schedulers.io()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable markObservationForDeletion(
      @NonNull ObservationEntity entity, ObservationMutation mutation) {
    return observationDao
        .update(entity.toBuilder().setState(EntityState.DELETED).build())
        .doOnSubscribe(__ -> Timber.d("Marking observation as deleted : %s", mutation))
        .ignoreElement()
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable deleteObservation(String observationId) {
    return observationDao
        .findById(observationId)
        .toSingle()
        .doOnSubscribe(__ -> Timber.d("Deleting local observation : %s", observationId))
        .flatMapCompletable(entity -> observationDao.delete(entity))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  private Completable enqueue(@NonNull ObservationMutation mutation) {
    return observationMutationDao
        .insert(ObservationMutationEntity.fromMutation(mutation))
        .doOnSubscribe(__ -> Timber.v("Enqueuing mutation: %s", mutation))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable insertOrUpdateTileSource(@NonNull TileSource tileSource) {
    return tileSourceDao
        .insertOrUpdate(TileSourceEntity.fromTile(tileSource))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Maybe<TileSource> getTileSource(String tileId) {
    return tileSourceDao
        .findById(tileId)
        .map(TileSourceEntity::toTileSource)
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<ImmutableList<TileSource>> getPendingTileSources() {
    return tileSourceDao
        .findByState(TileEntityState.PENDING.intValue())
        .map(ts -> stream(ts).map(TileSourceEntity::toTileSource).collect(toImmutableList()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Completable insertOrUpdateOfflineArea(@NonNull OfflineBaseMap area) {
    return offlineBaseMapDao
        .insertOrUpdate(OfflineBaseMapEntity.fromArea(area))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Flowable<ImmutableList<OfflineBaseMap>> getOfflineAreasOnceAndStream() {
    return offlineBaseMapDao
        .findAllOnceAndStream()
        .map(areas -> stream(areas).map(OfflineBaseMapEntity::toArea).collect(toImmutableList()))
        .subscribeOn(schedulers.io());
  }

  @NonNull
  @Override
  public Single<OfflineBaseMap> getOfflineAreaById(String id) {
    return offlineBaseMapDao
        .findById(id)
        .map(OfflineBaseMapEntity::toArea)
        .toSingle()
        .subscribeOn(schedulers.io());
  }
}
