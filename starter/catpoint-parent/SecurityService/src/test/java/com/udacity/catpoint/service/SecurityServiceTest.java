package com.udacity.catpoint.service;

import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import com.udacity.catpoint.data.SensorType;
import com.udacity.catpoint.imageservice.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    private SecurityService securityService;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
    }

    // 1
    @ParameterizedTest(name = "arming mode {0}: sensor activation sets pending alarm")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("If alarm is armed and a sensor becomes activated, put the system into pending alarm status.")
    void sensorActivation_whileArmed_setsPendingAlarm(ArmingStatus armingStatus) {
        // Arrange
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        // Act
        securityService.changeSensorActivationStatus(sensor, true);

        // Assert
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.PENDING_ALARM));
        verify(securityRepository).updateSensor(eq(sensor));
    }

    // 2
    @ParameterizedTest(name = "arming mode {0}: sensor activation while pending sets alarm")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("If alarm is armed and a sensor becomes activated and the system is already pending alarm, set off the alarm.")
    void sensorActivation_whilePending_setsAlarm(ArmingStatus armingStatus) {
        // Arrange
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Act
        securityService.changeSensorActivationStatus(sensor, true);

        // Assert
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
        verify(securityRepository).updateSensor(eq(sensor));
    }

    // 3 - inactive
    @Test
    @DisplayName("If pending alarm and all sensors are inactive, return to no alarm state.")
    void sensorDeactivation_whenAllSensorsInactive_returnsToNoAlarm() {
        // Arrange
        Sensor frontDoor = new Sensor("Front door", SensorType.DOOR);
        Sensor window = new Sensor("Living room window", SensorType.WINDOW);
        frontDoor.setActive(true);
        window.setActive(false);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(frontDoor);
        sensors.add(window);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Act
        securityService.changeSensorActivationStatus(frontDoor, false);

        // Assert
        assertFalse(frontDoor.getActive());
        assertFalse(window.getActive());
        verify(securityRepository).getSensors();
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
        verify(securityRepository).updateSensor(eq(frontDoor));
    }

    // 3 - active
    @Test
    @DisplayName("If pending alarm and another sensor remains active, keep the alarm pending.")
    void sensorDeactivation_whenAnotherSensorRemainsActive_keepsPendingAlarm() {
        // Arrange
        Sensor frontDoor = new Sensor("Front door", SensorType.DOOR);
        Sensor window = new Sensor("Living room window", SensorType.WINDOW);
        frontDoor.setActive(true);
        window.setActive(true);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(frontDoor);
        sensors.add(window);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Act
        securityService.changeSensorActivationStatus(frontDoor, false);

        // Assert
        assertFalse(frontDoor.getActive());
        assertTrue(window.getActive());
        verify(securityRepository).getSensors();
        verify(securityRepository).updateSensor(eq(frontDoor));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 4
    @ParameterizedTest(name = "sensor active={0}, requested active={1}: alarm remains active")
    @CsvSource({"false,true", "true,false"})
    @DisplayName("If alarm is active, change in sensor state should not affect the alarm state.")
    void sensorStateChange_whileAlarmActive_keepsAlarmActive(boolean initiallyActive, boolean nextActive) {
        // Arrange
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        sensor.setActive(initiallyActive);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        // Act
        securityService.changeSensorActivationStatus(sensor, nextActive);

        // Assert
        verify(securityRepository).updateSensor(eq(sensor));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 5
    @Test
    @DisplayName("If a sensor is activated while already active and the system is in pending state, change it to alarm state.")
    void sensorReactivated_whilePending_setsAlarm() {
        // Arrange
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        sensor.setActive(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Act
        securityService.changeSensorActivationStatus(sensor, true);

        // Assert
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
        verify(securityRepository).updateSensor(eq(sensor));
    }

    // 6
    @Test
    @DisplayName("If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    void sensorDeactivation_whileAlreadyInactive_keepsAlarmState() {
        // Arrange
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);

        // Act
        securityService.changeSensorActivationStatus(sensor, false);

        // Assert
        verify(securityRepository).updateSensor(eq(sensor));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 7
    @Test
    @DisplayName("If the camera image contains a cat while the system is armed-home, put the system into alarm status.")
    void catDetected_whileArmedHome_setsAlarm() {
        // Arrange
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(eq(image), eq(50.0f))).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        // Act
        securityService.processImage(image);

        // Assert
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    // 8 - inactive
    @Test
    @DisplayName("If the camera image does not contain a cat, change the status to no alarm as long as the sensors are not active.")
    void noCatDetected_whenSensorsInactive_setsNoAlarm() {
        // Arrange
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        sensor.setActive(false);
        Set<Sensor> sensors = Set.of(sensor);
        when(imageService.imageContainsCat(eq(image), eq(50.0f))).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);

        // Act
        securityService.processImage(image);

        // Assert
        assertFalse(sensor.getActive());
        verify(securityRepository).getSensors();
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    // 8 - active
    @Test
    @DisplayName("If the camera image does not contain a cat while a sensor is active, leave the alarm status unchanged.")
    void noCatDetected_whenSensorActive_keepsAlarmStatus() {
        // Arrange
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Sensor sensor = new Sensor("Front door", SensorType.DOOR);
        sensor.setActive(true);
        when(imageService.imageContainsCat(eq(image), eq(50.0f))).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(Set.of(sensor));
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Act
        securityService.processImage(image);

        // Assert
        assertTrue(sensor.getActive());
        verify(securityRepository).getSensors();
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // 9
    @Test
    @DisplayName("If the system is disarmed, set the status to no alarm.")
    void systemDisarmed_setsNoAlarm() {
        // Arrange: no additional setup is needed.

        // Act
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        // Assert
        verify(securityRepository).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
        verify(securityRepository).setArmingStatus(eq(ArmingStatus.DISARMED));
    }

    // 10
    @ParameterizedTest(name = "arming mode {0}: all sensors reset to inactive")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("If the system is armed, reset all sensors to inactive.")
    void systemArmed_resetsAllSensors(ArmingStatus armingStatus) {
        // Arrange
        Sensor frontDoor = new Sensor("Front door", SensorType.DOOR);
        Sensor window = new Sensor("Living room window", SensorType.WINDOW);
        frontDoor.setActive(true);
        window.setActive(true);
        Set<Sensor> sensors = new HashSet<>();
        sensors.add(frontDoor);
        sensors.add(window);
        when(securityRepository.getSensors()).thenReturn(sensors);

        // Act
        securityService.setArmingStatus(armingStatus);

        // Assert
        assertFalse(frontDoor.getActive());
        assertFalse(window.getActive());
        verify(securityRepository).updateSensor(eq(frontDoor));
        verify(securityRepository).updateSensor(eq(window));
        verify(securityRepository).setArmingStatus(eq(armingStatus));
    }

    // 11
    @Test
    @DisplayName("If armed-home is selected while the latest camera result shows a cat, set the alarm status to alarm.")
    void systemArmedHome_whenLatestCameraResultShowsCat_setsAlarm() {
        // Arrange
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(eq(image), eq(50.0f))).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);

        // Act
        securityService.processImage(image);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        // Assert
        var interactions = inOrder(imageService, securityRepository);
        interactions.verify(imageService).imageContainsCat(eq(image), eq(50.0f));
        interactions.verify(securityRepository).setArmingStatus(eq(ArmingStatus.ARMED_HOME));
        interactions.verify(securityRepository).setAlarmStatus(eq(AlarmStatus.ALARM));
    }
}
