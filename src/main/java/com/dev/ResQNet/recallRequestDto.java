package com.dev.ResQNet;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sent to the station manager over WebSocket when a worker requests a force recall,
 * and also used for the manager's approval/rejection notifications back to involved parties.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class recallRequestDto {

    /** ID of the recall request record. */
    private String recallId;

    /** ID of the dispatch being recalled. */
    private String dispatchId;

    /** ID of the disaster the dispatch belongs to. */
    private String disasterId;

    /** Station ID that owns the dispatch. */
    private String stationId;

    /** Station name. */
    private String stationName;

    /** Force type being recalled. */
    private Forces forceType;

    /** Username of the requesting worker. */
    private String requestingWorkerUsername;

    /** Reason supplied by the worker. */
    private String reason;

    /** When the request was created. */
    private LocalDateTime requestedAt;

    /** Current lifecycle status of the recall request. */
    private recallStatus status;

    /** Current status of the dispatch (updated on approval). */
    private Status dispatchStatus;
}
