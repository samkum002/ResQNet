package com.dev.ResQNet;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection = "recall_requests")
public class recallRequestEntity {

    public recallRequestEntity() {}

    @Id
    private ObjectId recallId;

    /** The specific dispatch being recalled. */
    private ObjectId dispatchId;

    /** Disaster the dispatch belongs to. */
    private ObjectId disasterId;

    /** Station that owns the dispatch. */
    private ObjectId stationId;

    /** Worker who submitted the recall request. */
    private ObjectId requestingWorkerId;

    /** Username of the requesting worker (for WebSocket lookup without an extra DB hit). */
    private String requestingWorkerUsername;

    /** Force type of the recalled dispatch (e.g. FIRE_DEPARTMENT). */
    private Forces forceType;

    /** Reason provided by the worker. */
    private String reason;

    /** Lifecycle status of the recall request itself. */
    private recallStatus status;

    @CreatedDate
    private LocalDateTime requestedAt;

    @LastModifiedDate
    private LocalDateTime decidedAt;

    @Version
    private Long version;
}
