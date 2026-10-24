import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Send, Camera, Paperclip, X, MapPin, AlertTriangle,
  Radio, CheckCircle2, Loader2, Navigation, Compass,
  Activity, ShieldCheck, Flame, Droplets, Mountain, Car
} from 'lucide-react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import DashboardHeader from '../components/DashboardHeader';
import { getCurrentUser } from '../api/auth';
import { reportDisaster, fetchUserReports, fetchStations } from '../api/disaster';
import { createStompClient } from '../api/websocket';
import './UserDashboard.css';

const QUICK_TAGS = ['Fire', 'Flood', 'Earthquake', 'Landslide', 'Accident'];

function upsertReport(list, incoming) {
  if (!incoming || !incoming.disasterId) return list;
  const index = list.findIndex((r) => r.disasterId === incoming.disasterId);
  if (index === -1) {
    return [incoming, ...list];
  }
  const next = [...list];
  next[index] = {
    ...next[index],
    ...incoming,
    cause: incoming.cause || next[index].cause || 'Reported Incident',
    lat: incoming.lat ?? next[index].lat,
    lng: incoming.lng ?? next[index].lng,
    time: incoming.time || next[index].time || new Date().toLocaleTimeString(),
  };
  return next;
}

export default function UserDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authChecking, setAuthChecking] = useState(true);

  // Form State
  const [keyword, setKeyword] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);
  const [imagePreview, setImagePreview] = useState(null);
  const [coords, setCoords] = useState({ lat: 28.6139, lng: 77.2090 }); // Default New Delhi
  const [locating, setLocating] = useState(false);
  const [locError, setLocError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  // Feed & Live Location
  const [reports, setReports] = useState([]);
  const [activeDispatches, setActiveDispatches] = useState({});
  const [stations, setStations] = useState([]);
  const [wsConnected, setWsConnected] = useState(false);
  const [mapReady, setMapReady] = useState(false);

  // Camera Capture Modal State
  const [cameraModalOpen, setCameraModalOpen] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const videoRef = useRef(null);
  const videoStreamRef = useRef(null);

  const fileInputRef = useRef(null);
  const cameraInputRef = useRef(null);
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const disasterMarkerRef = useRef(null);
  const vehicleMarkersRef = useRef(new Map());
  const routePolylinesRef = useRef(new Map());
  const autoFittedDispatchesRef = useRef(new Set());
  const stationLayerRef = useRef(null);
  const stompClientRef = useRef(null);

  // Auth Verification & Persistent Data Loading
  useEffect(() => {
    getCurrentUser().then((u) => {
      if (!u) {
        navigate('/login', { replace: true });
      } else {
        setUser(u);
        // Load persistent reports from MongoDB
        fetchUserReports()
          .then((data) => {
            if (Array.isArray(data)) {
              setReports(
                data.map((r) => ({
                  disasterId: r.disasterId,
                  status: r.status,
                  msg: r.msg,
                  cause: r.cause || 'Reported Incident',
                  time: r.createdAt ? new Date(r.createdAt).toLocaleTimeString() : new Date().toLocaleTimeString(),
                  lat: r.latitude,
                  lng: r.longitude,
                }))
              );
            }
          })
          .catch((err) => console.warn('Failed to load user reports:', err));

        // Load station coordinates for immediate map rendering
        fetchStations()
          .then((stList) => {
            if (Array.isArray(stList)) {
              setStations(stList);
            }
          })
          .catch((err) => console.warn('Failed to load stations:', err));
      }
      setAuthChecking(false);
    });
  }, [navigate]);

  // Initial Geolocation
  useEffect(() => {
    if ('geolocation' in navigator) {
      setLocating(true);
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          setCoords({
            lat: Number(pos.coords.latitude.toFixed(6)),
            lng: Number(pos.coords.longitude.toFixed(6)),
          });
          setLocating(false);
        },
        (err) => {
          setLocError('Could not auto-fetch GPS. Using preset coordinates.');
          setLocating(false);
        },
        { enableHighAccuracy: true, timeout: 8000 }
      );
    }
  }, []);

  // WebSocket / STOMP Connection
  useEffect(() => {
    if (!user) return;

    const client = createStompClient({
      onConnect: () => {
        setWsConnected(true);

        // 1. Report Status Updates (/user/queue/report)
        client.subscribe('/user/queue/report', (msg) => {
          try {
            const data = JSON.parse(msg.body);
            setReports((prev) =>
              upsertReport(prev, {
                disasterId: data.disasterId,
                status: data.status,
                msg: data.msg,
                time: new Date().toLocaleTimeString(),
              })
            );
          } catch (e) {
            console.error('Error parsing /queue/report message', e);
          }
        });

        // 2. Dispatch Live Location Tracking (/user/queue/dispatch-location)
        client.subscribe('/user/queue/dispatch-location', (msg) => {
          try {
            const loc = JSON.parse(msg.body);
            if (loc && loc.dispatchId) {
              setActiveDispatches((prev) => {
                if (
                  loc.arrived ||
                  loc.status === 'RECALLED' ||
                  loc.status === 'DISPATCH_COMPLETED' ||
                  loc.status === 'BACKUP_DISPATCH_COMPLETED' ||
                  loc.status === 'DISPATCH_FALSE'
                ) {
                  const next = { ...prev };
                  delete next[loc.dispatchId];
                  return next;
                }
                return { ...prev, [loc.dispatchId]: loc };
              });
            }
          } catch (e) {
            console.error('Error parsing /queue/dispatch-location message', e);
          }
        });
      },
      onDisconnect: () => {
        setWsConnected(false);
      },
      onError: (err) => {
        console.warn('STOMP connection error:', err);
      },
    });

    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [user]);

  // Leaflet Map Initialization - mounts as soon as authChecking is resolved
  useEffect(() => {
    if (authChecking || !mapContainerRef.current || mapInstanceRef.current) return;

    let resizeObserver = null;
    try {
      const map = L.map(mapContainerRef.current, {
        center: [coords.lat, coords.lng],
        zoom: 13,
        zoomControl: true,
      });

      // Standard OpenStreetMap tiles (100% free, no API key required)
      const tileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',
        maxZoom: 19,
      });
      tileLayer.on('tileerror', (e) => {
        console.warn('OSM tile load issue (safe fallback):', e);
      });
      tileLayer.addTo(map);

      mapInstanceRef.current = map;
      setMapReady(true);

      // Invalidate size in multiple intervals to ensure layout completes
      map.invalidateSize();
      setTimeout(() => map.invalidateSize(), 100);
      setTimeout(() => map.invalidateSize(), 300);
      setTimeout(() => map.invalidateSize(), 600);

      if (window.ResizeObserver && mapContainerRef.current) {
        resizeObserver = new ResizeObserver(() => {
          if (mapInstanceRef.current) {
            mapInstanceRef.current.invalidateSize();
          }
        });
        resizeObserver.observe(mapContainerRef.current);
      }
    } catch (err) {
      console.error('Leaflet map initialization error:', err);
    }

    const handleResize = () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.invalidateSize();
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (resizeObserver) {
        resizeObserver.disconnect();
      }
      if (stationLayerRef.current) {
        stationLayerRef.current.clearLayers();
        stationLayerRef.current = null;
      }
      if (disasterMarkerRef.current) {
        disasterMarkerRef.current.remove();
        disasterMarkerRef.current = null;
      }
      vehicleMarkersRef.current.forEach((marker) => marker.remove());
      vehicleMarkersRef.current.clear();
      routePolylinesRef.current.forEach((polyline) => polyline.remove());
      routePolylinesRef.current.clear();
      autoFittedDispatchesRef.current.clear();
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
      setMapReady(false);
    };
  }, [authChecking]);

  // Render Station Markers Immediately
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map || !mapReady || !stations || stations.length === 0) return;

    if (stationLayerRef.current) {
      stationLayerRef.current.clearLayers();
    } else {
      stationLayerRef.current = L.layerGroup().addTo(map);
    }

    const forceIcons = {
      FIRE_DEPARTMENT: '🚒',
      POLICE_STATION: '🚓',
      AMBULANCE: '🏥',
      ELECTRICITY_DEPARTMENT: '⚡',
      SDRF: '🛡️',
      NDRF: '🛡️',
    };

    const validStationLatLngs = [];

    stations.forEach((st) => {
      if (typeof st.latitude === 'number' && typeof st.longitude === 'number') {
        validStationLatLngs.push([st.latitude, st.longitude]);
        const iconSymbol = forceIcons[st.forceType] || '🏢';
        const stIcon = L.divIcon({
          className: 'marker-station',
          html: `<div class="marker-station-box">${iconSymbol}</div>`,
          iconSize: [30, 30],
          iconAnchor: [15, 15],
        });
        L.marker([st.latitude, st.longitude], { icon: stIcon })
          .bindPopup(`<b>${st.stationName}</b><br><span style="color:#00d4ff">${st.forceType ? st.forceType.replace(/_/g, ' ') : 'Station'}</span><br>State: ${st.state || 'N/A'}`)
          .addTo(stationLayerRef.current);
      }
    });

    // If stations exist, ensure they and the user position are within view
    if (validStationLatLngs.length > 0 && Object.keys(activeDispatches).length === 0) {
      try {
        const bounds = L.latLngBounds([[coords.lat, coords.lng], ...validStationLatLngs]);
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
      } catch (e) {
        // Safe catch
      }
    }
  }, [mapReady, stations]);

  // Update Map Markers on Active Dispatches or Location Change
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // Disaster Marker
    if (!disasterMarkerRef.current) {
      const incidentIcon = L.divIcon({
        className: 'marker-incident',
        html: '<div class="marker-pulse-red"></div><div class="marker-icon-box">🚨</div>',
        iconSize: [36, 36],
        iconAnchor: [18, 18],
      });
      disasterMarkerRef.current = L.marker([coords.lat, coords.lng], { icon: incidentIcon })
        .addTo(map)
        .bindPopup('<b>Incident Location</b><br>Reporting coordinates')
        .openPopup();
    } else {
      disasterMarkerRef.current.setLatLng([coords.lat, coords.lng]);
    }

    const currentDispatchIds = new Set(Object.keys(activeDispatches));

    // Remove obsolete vehicle markers
    vehicleMarkersRef.current.forEach((marker, id) => {
      if (!currentDispatchIds.has(id)) {
        marker.remove();
        vehicleMarkersRef.current.delete(id);
        autoFittedDispatchesRef.current.delete(id);
      }
    });

    // Remove obsolete polylines
    routePolylinesRef.current.forEach((polyline, id) => {
      if (!currentDispatchIds.has(id)) {
        polyline.remove();
        routePolylinesRef.current.delete(id);
      }
    });

    const activeList = Object.values(activeDispatches);
    const allPositions = [[coords.lat, coords.lng]];

    activeList.forEach((disp) => {
      if (typeof disp.latitude !== 'number' || typeof disp.longitude !== 'number') return;
      const vehiclePos = [disp.latitude, disp.longitude];
      allPositions.push(vehiclePos);

      let marker = vehicleMarkersRef.current.get(disp.dispatchId);
      if (!marker) {
        const vehicleIcon = L.divIcon({
          className: 'marker-vehicle',
          html: '<div class="marker-pulse-cyan"></div><div class="marker-icon-box">🚒</div>',
          iconSize: [38, 38],
          iconAnchor: [19, 19],
        });
        marker = L.marker(vehiclePos, { icon: vehicleIcon })
          .addTo(map)
          .bindPopup(
            `<b>Rescue Unit (${disp.dispatchId ? disp.dispatchId.slice(-6).toUpperCase() : 'UNIT'})</b><br>Status: ${disp.status || 'En Route'}`
          );
        vehicleMarkersRef.current.set(disp.dispatchId, marker);
      } else {
        marker.setLatLng(vehiclePos);
        marker.setPopupContent(
          `<b>Rescue Unit (${disp.dispatchId ? disp.dispatchId.slice(-6).toUpperCase() : 'UNIT'})</b><br>Status: ${disp.status || 'En Route'}<br>Progress: Point ${disp.routeIndex || 0}/${disp.totalRoutePoints || '?'}`
        );
      }

      // Draw dashed trajectory line towards the incident position
      const matchingReport = reports.find((r) => r.disasterId === disp.disasterId);
      const targetPos = matchingReport && typeof matchingReport.lat === 'number'
        ? [matchingReport.lat, matchingReport.lng]
        : [coords.lat, coords.lng];

      const routePoints = [vehiclePos, targetPos];
      let polyline = routePolylinesRef.current.get(disp.dispatchId);
      if (!polyline) {
        polyline = L.polyline(routePoints, {
          color: '#00d4ff',
          weight: 3,
          dashArray: '6, 8',
          opacity: 0.8,
        }).addTo(map);
        routePolylinesRef.current.set(disp.dispatchId, polyline);
      } else {
        polyline.setLatLngs(routePoints);
      }
    });

    let hasNewDispatch = false;
    activeList.forEach((disp) => {
      if (disp.dispatchId && !autoFittedDispatchesRef.current.has(disp.dispatchId)) {
        autoFittedDispatchesRef.current.add(disp.dispatchId);
        hasNewDispatch = true;
      }
    });

    // Auto-fit bounds ONLY once when a new dispatch starts to avoid continuous 2s viewport jitter
    // Station markers remain static at their database coordinates while vehicles move smoothly
    if (hasNewDispatch && activeList.length > 0 && allPositions.length > 1) {
      try {
        const bounds = L.latLngBounds(allPositions);
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
      } catch (e) {
        // Safe fallback
      }
    }
  }, [mapReady, activeDispatches, coords, reports]);

  // Handle Image Selection
  const handleImageChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedImage(file);
      const url = URL.createObjectURL(file);
      setImagePreview(url);
    }
  };

  const removeImage = () => {
    setSelectedImage(null);
    if (imagePreview) URL.revokeObjectURL(imagePreview);
    setImagePreview(null);
  };

  // Device detection helper
  const isMobileDevice = () => {
    return (
      /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) ||
      (window.matchMedia && window.matchMedia('(max-width: 768px)').matches && 'ontouchstart' in window)
    );
  };

  const stopCameraStream = () => {
    if (videoStreamRef.current) {
      videoStreamRef.current.getTracks().forEach((track) => track.stop());
      videoStreamRef.current = null;
    }
  };

  // Trigger camera capture: mobile uses native capture="environment", desktop opens live webcam modal
  const handleCameraClick = async () => {
    if (isMobileDevice()) {
      cameraInputRef.current?.click();
      return;
    }

    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      setCameraError('');
      setCameraModalOpen(true);
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment', width: { ideal: 1280 }, height: { ideal: 720 } },
          audio: false,
        });
        videoStreamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          videoRef.current.play().catch((e) => console.warn('Video play error:', e));
        }
      } catch (err) {
        console.warn('Webcam stream unavailable:', err);
        setCameraError('Camera stream unavailable. Switching to image selector...');
        setTimeout(() => {
          stopCameraStream();
          setCameraModalOpen(false);
          cameraInputRef.current?.click();
        }, 1200);
      }
    } else {
      cameraInputRef.current?.click();
    }
  };

  // Capture frame from active desktop video stream
  const handleSnapPhoto = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 640;
    canvas.height = video.videoHeight || 480;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    canvas.toBlob(
      (blob) => {
        if (blob) {
          const file = new File([blob], `incident-photo-${Date.now()}.jpg`, { type: 'image/jpeg' });
          setSelectedImage(file);
          const previewUrl = URL.createObjectURL(file);
          setImagePreview(previewUrl);
        }
        stopCameraStream();
        setCameraModalOpen(false);
      },
      'image/jpeg',
      0.92
    );
  };

  const handleCloseCameraModal = () => {
    stopCameraStream();
    setCameraModalOpen(false);
  };

  // Clean up camera stream on unmount
  useEffect(() => {
    return () => {
      stopCameraStream();
    };
  }, []);

  // Validate single alphabetic word
  const isValidKeyword = /^[A-Za-z]+$/.test(keyword.trim());

  const handleRefreshLocation = () => {
    if ('geolocation' in navigator) {
      setLocating(true);
      setLocError('');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const newCoords = {
            lat: Number(pos.coords.latitude.toFixed(6)),
            lng: Number(pos.coords.longitude.toFixed(6)),
          };
          setCoords(newCoords);
          if (mapInstanceRef.current) {
            mapInstanceRef.current.setView([newCoords.lat, newCoords.lng], 14);
          }
          setLocating(false);
        },
        (err) => {
          setLocError('Failed to refresh GPS coordinates.');
          setLocating(false);
        },
        { enableHighAccuracy: true, timeout: 8000 }
      );
    }
  };

  const handleSubmitReport = async (e) => {
    e.preventDefault();
    if (!selectedImage) {
      setFormError('Please attach an incident photo before transmitting.');
      return;
    }
    if (!isValidKeyword) {
      setFormError('Disaster cause must be exactly one alphabetic word (e.g. Fire, Flood).');
      return;
    }

    setSubmitting(true);
    setFormError('');

    try {
      const response = await reportDisaster({
        imageFile: selectedImage,
        latitude: coords.lat,
        longitude: coords.lng,
        userReport: keyword.trim(),
      });

      // Add to local reports timeline with deduplication
      setReports((prev) =>
        upsertReport(prev, {
          disasterId: response.disasterId,
          status: response.status || 'REPORTED',
          msg: response.msg || 'Transmitted to Command Center',
          cause: keyword.trim(),
          time: new Date().toLocaleTimeString(),
          lat: coords.lat,
          lng: coords.lng,
        })
      );

      // Reset input form
      setKeyword('');
      removeImage();
    } catch (err) {
      setFormError(err.message || 'Report transmission failed. Please retry.');
    } finally {
      setSubmitting(false);
    }
  };

  if (authChecking) {
    return (
      <div className="dash-loading" aria-label="Authenticating session">
        <Loader2 size={32} className="spin" />
      </div>
    );
  }

  return (
    <div className="user-dash-page">
      <DashboardHeader user={user} roleTitle="Civilian Reporter" />

      <main className="user-dash-container">
        {/* Left Side: Conversational Reporting Studio */}
        <section className="reporter-panel">
          <div className="panel-title-bar">
            <div className="bar-tag">
              <Radio size={14} className={wsConnected ? 'stream-active' : ''} />
              <span>EMERGENCY TRANSMISSION TERMINAL</span>
            </div>
            <div className="connection-badge">
              <span className={`signal-dot ${wsConnected ? 'online' : 'reconnecting'}`} />
              <span>{wsConnected ? 'RADAR SYNCED' : 'OFFLINE'}</span>
            </div>
          </div>

          {/* Quick Select Preset Causes */}
          <div className="quick-tags-wrap">
            <span className="quick-tags-label">Quick Tag:</span>
            {QUICK_TAGS.map((tag) => (
              <button
                key={tag}
                type="button"
                className={`tag-btn ${keyword.toLowerCase() === tag.toLowerCase() ? 'active' : ''}`}
                onClick={() => setKeyword(tag)}
              >
                {tag}
              </button>
            ))}
          </div>

          {/* Message Feed / Timeline */}
          <div className="incident-timeline">
            {reports.length === 0 ? (
              <div className="timeline-empty">
                <ShieldCheck size={42} className="empty-shield" />
                <h3>No active incident reports</h3>
                <p>Attach a disaster image, enter a one-word cause, and dispatch your emergency report.</p>
              </div>
            ) : (
              reports.map((r, i) => (
                <div key={r.disasterId || i} className="report-card animate-fade-in">
                  <div className="report-card-header">
                    <div className="report-badge">
                      <Flame size={14} />
                      <span className="report-cause">{r.cause}</span>
                    </div>
                    <span className="report-time">{r.time}</span>
                  </div>

                  <div className="report-body">
                    <p className="report-status-msg">{r.msg}</p>
                    <div className="report-meta">
                      <span className="report-id">Incident #{r.disasterId ? r.disasterId.slice(-6).toUpperCase() : 'NEW'}</span>
                      <span className="meta-sep">•</span>
                      <span className="status-pill status-active">{r.status}</span>
                    </div>
                  </div>
                </div>
              ))
            )}

            {/* Active Live Simulation Status Banners */}
            {Object.values(activeDispatches).map((ad) => (
              <div key={ad.dispatchId} className="dispatch-live-card animate-pulse">
                <div className="live-card-header">
                  <Car size={16} className="text-cyan" />
                  <h4>Rescue Force In Transit ({ad.dispatchId ? ad.dispatchId.slice(-6).toUpperCase() : 'UNIT'})</h4>
                  <span className="live-tag">LIVE SIMULATION</span>
                </div>
                <p className="live-status-text">
                  Emergency responders deployed. Moving along route to incident #{ad.disasterId ? ad.disasterId.slice(-6).toUpperCase() : 'REPORT'}.
                </p>
                <div className="live-progress-bar">
                  <div
                    className="live-progress-fill"
                    style={{
                      width: `${
                        ad.totalRoutePoints
                          ? Math.min(100, Math.round(((ad.routeIndex || 0) / ad.totalRoutePoints) * 100))
                          : 40
                      }%`,
                    }}
                  />
                </div>
                <div className="live-metrics">
                  <span>Status: <strong>{ad.status || 'EN_ROUTE'}</strong></span>
                  <span>Checkpoint: <strong>{ad.routeIndex || 0}/{ad.totalRoutePoints || '10'}</strong></span>
                </div>
              </div>
            ))}
          </div>

          {/* Location Bar */}
          <div className="location-bar">
            <div className="loc-info">
              <MapPin size={15} className="text-cyan" />
              <span>GPS: {coords.lat.toFixed(4)}°N, {coords.lng.toFixed(4)}°E</span>
              {locating && <Loader2 size={13} className="spin text-cyan" />}
            </div>
            <button
              type="button"
              className="btn-loc-refresh"
              onClick={handleRefreshLocation}
              disabled={locating}
              title="Refresh GPS Coordinates"
            >
              <Navigation size={13} />
              <span>Locate</span>
            </button>
          </div>
          {locError && <div className="loc-warning">{locError}</div>}

          {/* Image Preview Container */}
          {imagePreview && (
            <div className="preview-container">
              <img src={imagePreview} alt="Incident preview" className="preview-img" />
              <button type="button" className="btn-remove-img" onClick={removeImage} aria-label="Remove photo">
                <X size={14} />
              </button>
              <span className="preview-label">Incident Imagery Attached</span>
            </div>
          )}

          {formError && (
            <div className="form-error-banner" role="alert">
              <AlertTriangle size={15} />
              <span>{formError}</span>
            </div>
          )}

          {/* Conversational Composer Bar */}
          <form onSubmit={handleSubmitReport} className="composer-form" noValidate>
            {/* Attachment Actions */}
            <div className="composer-actions">
              <input
                type="file"
                ref={fileInputRef}
                accept="image/*"
                onChange={handleImageChange}
                style={{ display: 'none' }}
                id="file-input"
              />
              <input
                type="file"
                ref={cameraInputRef}
                accept="image/*"
                capture="environment"
                onChange={handleImageChange}
                style={{ display: 'none' }}
                id="camera-input"
              />

              <button
                type="button"
                className="action-icon-btn"
                onClick={() => fileInputRef.current?.click()}
                title="Attach photo from gallery"
                aria-label="Upload photo"
              >
                <Paperclip size={18} />
              </button>

              <button
                type="button"
                className="action-icon-btn camera-btn"
                onClick={handleCameraClick}
                title="Take incident photo with camera"
                aria-label="Open camera"
              >
                <Camera size={18} />
              </button>
            </div>

            {/* Keyword Input */}
            <div className="input-field-wrap">
              <input
                type="text"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value.replace(/[^A-Za-z]/g, ''))}
                placeholder="Disaster cause (one word: Fire, Flood...)"
                aria-label="Disaster cause keyword"
                disabled={submitting}
                maxLength={20}
              />
              {keyword && (
                <span className={`validation-badge ${isValidKeyword ? 'valid' : 'invalid'}`}>
                  {isValidKeyword ? 'Valid Keyword' : 'Alpha Only'}
                </span>
              )}
            </div>

            {/* Transmit Button */}
            <button
              type="submit"
              className="btn-transmit"
              disabled={submitting || !selectedImage || !isValidKeyword}
              aria-label="Transmit Disaster Report"
              aria-busy={submitting}
            >
              {submitting ? (
                <Loader2 size={18} className="spin" />
              ) : (
                <>
                  <span>Transmit</span>
                  <Send size={15} />
                </>
              )}
            </button>
          </form>
        </section>

        {/* Right Side: Tactical Live Map */}
        <section className="tactical-map-panel" aria-label="Tactical Rescue Map">
          <div className="map-overlay-header">
            <div className="overlay-left">
              <Compass size={16} className="text-cyan" />
              <span>TACTICAL SITUATION MAP</span>
            </div>
            <div className="overlay-right">
              <span>{Object.keys(activeDispatches).length > 0 ? `DISPATCH TRACKING ACTIVE (${Object.keys(activeDispatches).length} UNITS)` : 'RADAR ACTIVE • ' + stations.length + ' STATIONS'}</span>
            </div>
          </div>

          <div ref={mapContainerRef} className="leaflet-map-canvas" />

          {/* Map Status Footer */}
          <div className="map-overlay-footer">
            <div className="map-legend-item">
              <span className="legend-dot dot-red" />
              <span>Incident Focus</span>
            </div>
            <div className="map-legend-item">
              <span className="legend-dot dot-blue" />
              <span>Emergency Stations</span>
            </div>
            <div className="map-legend-item">
              <span className="legend-dot dot-cyan" />
              <span>Dispatched Unit</span>
            </div>
            <div className="map-legend-item">
              <span className="legend-dot dot-dashed" />
              <span>Live Vector</span>
            </div>
          </div>
        </section>
      </main>

      {/* Camera Capture Modal (Desktop Live Stream) */}
      {cameraModalOpen && (
        <div className="camera-modal-backdrop" role="dialog" aria-modal="true" aria-label="Camera Capture">
          <div className="camera-modal-card">
            <div className="camera-modal-header">
              <div className="modal-title-wrap">
                <Camera size={18} className="text-cyan" />
                <h3>Live Incident Camera</h3>
              </div>
              <button
                type="button"
                className="btn-modal-close"
                onClick={handleCloseCameraModal}
                aria-label="Close camera"
              >
                <X size={18} />
              </button>
            </div>

            <div className="camera-video-wrapper">
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className="camera-video-element"
              />
              {cameraError && <div className="camera-error-banner">{cameraError}</div>}
            </div>

            <div className="camera-modal-footer">
              <button
                type="button"
                className="btn-camera-cancel"
                onClick={handleCloseCameraModal}
              >
                Cancel
              </button>
              <button
                type="button"
                className="btn-camera-snap"
                onClick={handleSnapPhoto}
                disabled={!!cameraError}
              >
                <Camera size={16} />
                <span>Capture Photo</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
