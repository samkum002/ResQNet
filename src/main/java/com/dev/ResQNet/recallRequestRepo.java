package com.dev.ResQNet;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface recallRequestRepo extends MongoRepository<recallRequestEntity, ObjectId> {

    /** Find any existing recall request for a given dispatch and status. */
    Optional<recallRequestEntity> findByDispatchIdAndStatus(ObjectId dispatchId, recallStatus status);

    /** All recall requests for a specific dispatch (any status). */
    List<recallRequestEntity> findByDispatchId(ObjectId dispatchId);

    /** All recall requests associated with a station. */
    List<recallRequestEntity> findByStationId(ObjectId stationId);
}
