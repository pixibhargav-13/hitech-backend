package com.hitech.erp.payroll.service;

import com.hitech.erp.common.exception.EntityNotFoundException;
import com.hitech.erp.payroll.db.LocationEntity;
import com.hitech.erp.payroll.db.LocationRepository;
import com.hitech.erp.payroll.dto.PayrollDtos.GeoPoint;
import com.hitech.erp.payroll.dto.PayrollDtos.LocationRequest;
import com.hitech.erp.payroll.dto.PayrollDtos.LocationResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Work-site geofences (polygons) + which members may punch at each. The punch flow calls
 * {@link #assertInsideAssignedSite} so a member can only punch in/out while physically inside one of
 * their assigned sites — the "geofence" the client asked for, enforced server-side.
 *
 * Storage is dependency-free delimited text (payroll-service has no JSON lib on its classpath):
 * points = "lat,lng;lat,lng;…", memberIds = "id,id,…". Never exposed raw — always mapped to DTOs.
 */
@Service
@RequiredArgsConstructor
public class LocationService {

  private final LocationRepository locationRepository;

  // ---- Reads ----

  @Transactional(readOnly = true)
  public List<LocationResponse> getAll() {
    return locationRepository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
  }

  /** Locations the given member can punch at — directly assigned OR via a linked project they belong to. */
  @Transactional(readOnly = true)
  public List<LocationResponse> getForMember(Long userId) {
    return effectiveSitesForMember(userId).stream().map(this::toResponse).toList();
  }

  /** The sites a member may punch at: directly-assigned + any site linked to a project they're in. */
  private List<LocationEntity> effectiveSitesForMember(Long userId) {
    Set<Long> myProjects = new HashSet<>(locationRepository.findProjectIdsForMember(userId));
    return locationRepository.findAllByOrderByNameAsc().stream()
        .filter(l -> parseIds(l.getMemberIds()).contains(userId)
            || (l.getProjectId() != null && myProjects.contains(l.getProjectId())))
        .toList();
  }

  // ---- Writes ----

  @Transactional
  public LocationResponse create(LocationRequest r) {
    LocationEntity e = new LocationEntity();
    apply(e, r);
    return toResponse(locationRepository.save(e));
  }

  @Transactional
  public LocationResponse update(Long id, LocationRequest r) {
    LocationEntity e = locationRepository.findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Location not found: " + id));
    apply(e, r);
    return toResponse(locationRepository.save(e));
  }

  @Transactional
  public void delete(Long id) {
    if (!locationRepository.existsById(id)) throw new EntityNotFoundException("Location not found: " + id);
    locationRepository.deleteById(id);
  }

  // ---- Geofence enforcement (called from the punch flow) ----

  /**
   * Throw (422) unless the member is inside one of their assigned sites at (lat,lng). Enforces the
   * "punch only inside the geofence" rule server-side, so a spoofed client can't bypass it.
   */
  @Transactional(readOnly = true)
  public void assertInsideAssignedSite(Long userId, Double lat, Double lng) {
    List<LocationEntity> mine = effectiveSitesForMember(userId);
    if (mine.isEmpty()) {
      throw new IllegalStateException("No work site is assigned to you yet — ask your admin to assign one before you punch.");
    }
    if (lat == null || lng == null) {
      throw new IllegalStateException("Your location is required to punch — please enable GPS / location access.");
    }
    boolean inside = mine.stream().anyMatch(l -> isInside(lat, lng, parsePoints(l.getPoints())));
    if (!inside) {
      throw new IllegalStateException("You must be inside an assigned work site to punch in or out.");
    }
  }

  /** Ray-casting point-in-polygon over the vertices (shape treated as closed). Mirrors the client. */
  static boolean isInside(double lat, double lng, List<GeoPoint> pts) {
    if (pts == null || pts.size() < 3) return false;
    boolean inside = false;
    for (int i = 0, j = pts.size() - 1; i < pts.size(); j = i++) {
      double yi = pts.get(i).lat(), xi = pts.get(i).lng();
      double yj = pts.get(j).lat(), xj = pts.get(j).lng();
      boolean intersects = (yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi;
      if (intersects) inside = !inside;
    }
    return inside;
  }

  // ---- Helpers ----

  private void apply(LocationEntity e, LocationRequest r) {
    e.setName(r.name().trim());
    List<GeoPoint> pts = r.points() == null ? List.of() : r.points();
    e.setPoints(pts.stream().map(p -> p.lat() + "," + p.lng()).collect(Collectors.joining(";")));
    List<Long> ids = r.memberIds() == null ? List.of() : r.memberIds().stream().distinct().toList();
    e.setMemberIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
    e.setProjectId(r.projectId());
  }

  private LocationResponse toResponse(LocationEntity e) {
    String projectName = e.getProjectId() == null
        ? null
        : locationRepository.findProjectName(e.getProjectId()).orElse(null);
    return new LocationResponse(
        e.getId(), e.getName(), parsePoints(e.getPoints()), parseIds(e.getMemberIds()),
        e.getProjectId(), projectName);
  }

  private static List<GeoPoint> parsePoints(String s) {
    if (s == null || s.isBlank()) return List.of();
    List<GeoPoint> out = new ArrayList<>();
    for (String pair : s.split(";")) {
      String[] xy = pair.split(",");
      if (xy.length == 2) {
        try {
          out.add(new GeoPoint(Double.parseDouble(xy[0].trim()), Double.parseDouble(xy[1].trim())));
        } catch (NumberFormatException ignored) {
          // skip a malformed vertex rather than failing the whole read
        }
      }
    }
    return out;
  }

  private static List<Long> parseIds(String s) {
    if (s == null || s.isBlank()) return List.of();
    List<Long> out = new ArrayList<>();
    for (String id : s.split(",")) {
      try {
        out.add(Long.parseLong(id.trim()));
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    return out;
  }
}
