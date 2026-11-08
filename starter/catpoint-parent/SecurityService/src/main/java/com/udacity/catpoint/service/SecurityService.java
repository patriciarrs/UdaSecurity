package com.udacity.catpoint.service;

import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import com.udacity.catpoint.imageservice.ImageService;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates security-system state changes in response to arming selections, sensor updates,
 * and camera scans. Persists state through the repository and notifies registered listeners
 * when alarm status or camera-detection results change.
 *
 * <p>Sensor activation while armed moves the alarm from {@link AlarmStatus#NO_ALARM} to
 * {@link AlarmStatus#PENDING_ALARM}, then from pending to {@link AlarmStatus#ALARM} on another
 * activation. A pending alarm clears only after every sensor is inactive. Once the alarm is
 * active, sensor changes do not clear it. A cat detected while armed-home triggers the alarm;
 * a scan without a cat clears it only when no sensor is active.</p>
 */
public class SecurityService {

    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    /** Last camera result, used if the system is subsequently armed-home. */
    private boolean latestImageContainsCat;

    /**
     * Creates a service using the supplied persistence and image-analysis dependencies.
     *
     * @param securityRepository stores and supplies arming, alarm, and sensor state
     * @param imageService analyzes camera images for cats
     */
    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Changes the system's arming mode. Disarming clears the alarm. Selecting either armed mode
     * resets active sensors before persisting the new mode, and notifies listeners of the sensor
     * change if any sensor was reset. If the latest camera scan detected
     * a cat, selecting armed-home then sets the alarm after the new mode has been persisted.
     *
     * @param armingStatus the mode to apply
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if(armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            // Copy first: updateSensor may modify the repository's live sensor set.
            List<Sensor> activeSensors = securityRepository.getSensors().stream()
                    .filter(sensor -> Boolean.TRUE.equals(sensor.getActive()))
                    .collect(Collectors.toCollection(ArrayList::new));

            activeSensors.forEach(sensor -> {
                sensor.setActive(false);
                securityRepository.updateSensor(sensor);
            });

            if(!activeSensors.isEmpty()) {
                notifySensorStatusChanged();
            }
        }

        securityRepository.setArmingStatus(armingStatus);

        if(armingStatus == ArmingStatus.ARMED_HOME && latestImageContainsCat) {
            setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Applies a camera result to the alarm state and informs listeners of the scan result.
     * A detected cat triggers the alarm only in armed-home mode. A negative result clears the
     * alarm only when all sensors are inactive; otherwise the current alarm state is preserved.
     *
     * @param cat {@code true} when the image service detected a cat
     */
    private void catDetected(Boolean cat) {
        latestImageContainsCat = Boolean.TRUE.equals(cat);

        if(latestImageContainsCat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if(!latestImageContainsCat
                && getSensors().stream().noneMatch(sensor -> Boolean.TRUE.equals(sensor.getActive()))) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Registers a listener for alarm-status and camera-result notifications.
     *
     * @param statusListener listener to register
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    /**
     * Removes a previously registered listener. Removing a listener that is not registered
     * has no effect.
     *
     * @param statusListener listener to remove
     */
    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Persists an alarm status and notifies all registered listeners of the new status.
     *
     * @param status alarm state to persist and publish
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Handles a transition from inactive to active for a sensor. Disarmed systems ignore the
     * activation for alarm purposes; armed systems enter pending status from no alarm and alarm
     * status from pending. An already active alarm remains unchanged.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }

        switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Handles a sensor transition from active to inactive. A pending alarm is cleared only if all
     * other sensors are inactive. The specified sensor is excluded because its updated state
     * has not yet been written to the repository when this method runs. An active alarm stays
     * active.
     *
     * @param deactivatedSensor sensor whose state is about to be persisted as inactive
     */
    private void handleSensorDeactivated(Sensor deactivatedSensor) {
        if (Objects.requireNonNull(securityRepository.getAlarmStatus()) == AlarmStatus.PENDING_ALARM) {
            if (securityRepository.getSensors().stream()
                    .filter(sensor -> !sensor.equals(deactivatedSensor))
                    .noneMatch(sensor -> Boolean.TRUE.equals(sensor.getActive()))) {
                setAlarmStatus(AlarmStatus.NO_ALARM);
            }
        }
    }

    /**
     * Changes a sensor's activation state, updates the alarm state when required, then persists
     * the sensor and notifies listeners of the sensor change. Repeating an activation while an alarm is pending escalates to alarm status;
     * redundant deactivation does not cause an alarm transition.
     *
     * @param sensor sensor to update
     * @param active {@code true} to activate the sensor, {@code false} to deactivate it
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        boolean wasActive = Boolean.TRUE.equals(sensor.getActive());
        boolean isActive = Boolean.TRUE.equals(active);

        if(!wasActive && isActive) {
            handleSensorActivated();
        } else if(wasActive && isActive
                && securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (wasActive && !isActive) {
            handleSensorDeactivated(sensor);
        }

        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
        notifySensorStatusChanged();
    }

    /**
     * Informs all registered listeners that one or more sensor states have changed.
     */
    private void notifySensorStatusChanged() {
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Analyzes a camera image using a confidence threshold of {@code 50.0}, then applies the
     * result to the alarm state and notifies listeners of the detection result.
     *
     * @param currentCameraImage image from the security camera
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    /**
     * Returns the currently persisted alarm state.
     *
     * @return current alarm state
     */
    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    /**
     * Returns the sensors currently known to the repository.
     *
     * @return repository sensor set
     */
    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    /**
     * Adds a sensor to the repository.
     *
     * @param sensor sensor to add
     */
    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    /**
     * Removes a sensor from the repository.
     *
     * @param sensor sensor to remove
     */
    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    /**
     * Returns the currently persisted arming mode.
     *
     * @return current arming mode
     */
    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
