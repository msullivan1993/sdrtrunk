/*
 * *****************************************************************************
 * Copyright (C) 2014-2025 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */

package io.github.dsheirer.source.tuner.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.dsheirer.settings.Settings;
import io.github.dsheirer.source.tuner.TunerFactory;
import io.github.dsheirer.source.tuner.TunerType;
import io.github.dsheirer.source.tuner.manager.DiscoveredTuner;
import io.github.dsheirer.source.tuner.manager.IDiscoveredTunerStatusListener;
import io.github.dsheirer.source.tuner.manager.TunerStatus;
import io.github.dsheirer.util.ThreadPool;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages settings and configurations for all tuner types.
 */
public class TunerConfigurationManager implements IDiscoveredTunerStatusListener
{
    private static final Logger mLog = LoggerFactory.getLogger(TunerConfigurationManager.class);
    private static final String SETTINGS_FILE_NAME = "tuner_configuration.json";
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String TEMP_SUFFIX = ".tmp";
    private Settings mSettings;
    private List<DisabledTuner> mDisabledTunerList = new ArrayList<>();
    private List<TunerConfiguration> mTunerConfigurations = new ArrayList<>();
    private AtomicBoolean mSavePending = new AtomicBoolean();
    private Lock mLock = new ReentrantLock();

    /**
     * Constructs an instance and loads the save configuration state.
     *
     * @param settings to determine directories for accessing files
     */
    public TunerConfigurationManager(Settings settings)
    {
        mSettings = settings;
        load();
    }

    /**
     * Loads settings from the persisted tuner state file.
     *
     * If the file can't be read (for example, truncated or zero-length after a power loss during a save), the
     * damaged file is set aside and the last-known-good backup is loaded instead.  After every successful load of
     * the primary file, that file is copied to the backup so the backup always holds the most recent configuration
     * that loaded cleanly.
     */
    private void load()
    {
        Path configPath = getConfigurationFilePath();
        Path backupPath = getBackupFilePath();

        if(Files.exists(configPath))
        {
            TunerConfigurationState state = read(configPath);

            if(state != null)
            {
                apply(state);

                try
                {
                    Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                }
                catch(IOException ioe)
                {
                    mLog.warn("Unable to update tuner configuration backup [" + backupPath + "]", ioe);
                }

                return;
            }

            //Primary file is damaged - set it aside so the next save can't destroy the evidence, then fall back
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Path damagedPath = configPath.resolveSibling(SETTINGS_FILE_NAME + ".damaged-" + stamp);

            try
            {
                Files.move(configPath, damagedPath, StandardCopyOption.REPLACE_EXISTING);
                mLog.error("Tuner configuration file was unreadable - moved to [" + damagedPath + "]");
            }
            catch(IOException ioe)
            {
                mLog.error("Tuner configuration file was unreadable and could not be moved aside", ioe);
            }
        }

        if(Files.exists(backupPath))
        {
            TunerConfigurationState state = read(backupPath);

            if(state != null)
            {
                apply(state);
                mLog.warn("Tuner configuration restored from backup [" + backupPath + "]");

                try
                {
                    Files.copy(backupPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
                catch(IOException ioe)
                {
                    mLog.warn("Unable to restore tuner configuration file from backup", ioe);
                }

                return;
            }

            mLog.error("Tuner configuration backup [" + backupPath + "] is also unreadable - using defaults");
        }
    }

    /**
     * Reads a tuner configuration state file.
     * @param path to read
     * @return state, or null if the file is empty or can't be parsed.
     */
    private TunerConfigurationState read(Path path)
    {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try
        {
            if(Files.size(path) == 0)
            {
                mLog.error("Tuner configuration file [" + path + "] is empty");
                return null;
            }

            TunerConfigurationState state = objectMapper.readValue(path.toFile(), TunerConfigurationState.class);

            if(state == null || state.getTunerConfigurations() == null || state.getDisabledTuners() == null)
            {
                mLog.error("Tuner configuration file [" + path + "] is incomplete");
                return null;
            }

            return state;
        }
        catch(Exception e)
        {
            mLog.error("Error loading tuner configuration file [" + path + "]", e);
            return null;
        }
    }

    /**
     * Applies a loaded state to this manager.
     */
    private void apply(TunerConfigurationState state)
    {
        mDisabledTunerList.addAll(state.getDisabledTuners());
        mTunerConfigurations.addAll(state.getTunerConfigurations());
    }

    /**
     * Tuner configuration state file (.json).
     */
    private Path getConfigurationFilePath()
    {
        return mSettings.getDirectorySettings().getDirectoryConfiguration().resolve(SETTINGS_FILE_NAME);
    }

    /**
     * Last-known-good copy of the tuner configuration state file.
     */
    private Path getBackupFilePath()
    {
        return getConfigurationFilePath().resolveSibling(SETTINGS_FILE_NAME + BACKUP_SUFFIX);
    }

    /**
     * Saves the current tuner configuration state to disk.
     */
    private void save()
    {
        TunerConfigurationState state = new TunerConfigurationState();

        mLock.lock();

        try
        {
            state.setDisabledTuners(new ArrayList<>(mDisabledTunerList));
            state.setTunerConfigurations(new ArrayList<>(mTunerConfigurations));
        }
        catch(Exception e)
        {
            mLog.error("Error", e);
        }
        finally
        {
            mLock.unlock();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        //Write to a temporary file, force it to disk, then swap it into place, so a power loss mid-save leaves
        //either the previous file or the new file intact, never a truncated one.
        Path configPath = getConfigurationFilePath();
        Path tempPath = configPath.resolveSibling(SETTINGS_FILE_NAME + TEMP_SUFFIX);

        try
        {
            byte[] bytes = objectMapper.writeValueAsBytes(state);

            try(FileOutputStream out = new FileOutputStream(tempPath.toFile()))
            {
                out.write(bytes);
                out.flush();
                out.getFD().sync();
            }

            try
            {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch(AtomicMoveNotSupportedException amnse)
            {
                Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch(IOException ioe)
        {
            mLog.error("Error writing tuner configuration state to file [" + configPath + "]", ioe);
        }
    }

    /**
     * Schedules a configurations save task.  Subsequent calls to this method will be ignored until the
     * save event occurs, thus limiting repetitive saving to a minimum.
     */
    public void saveConfigurations()
    {
        if(mSavePending.compareAndSet(false, true))
        {
            ThreadPool.SCHEDULED.schedule(new ConfigurationSaveTask(), 2, TimeUnit.SECONDS);
        }
    }

    /**
     * Monitors discovered tuner enabled status and applies configurations or updates disable state of tuners.
     * @param discoveredTuner that has a status change.
     * @param previous tuner status
     * @param current tuner status
     */
    @Override
    public void tunerStatusUpdated(DiscoveredTuner discoveredTuner, TunerStatus previous, TunerStatus current)
    {
        if(current == TunerStatus.DISABLED)
        {
            addDisabledTuner(discoveredTuner);
        }
        else if(current == TunerStatus.ENABLED)
        {
            removeDisabledTuner(discoveredTuner);

            if(discoveredTuner.hasTuner())
            {
                TunerType tunerType = discoveredTuner.getTuner().getTunerType();

                //Recording and network tuners' getId() doesn't match the configuration's uniqueID scheme
                if(tunerType != TunerType.RECORDING && tunerType != TunerType.RTL_TCP)
                {
                    TunerConfiguration tunerConfiguration = getTunerConfiguration(tunerType, discoveredTuner.getId());

                    if(tunerConfiguration != null)
                    {
                        discoveredTuner.setTunerConfiguration(tunerConfiguration);
                        saveConfigurations();
                    }
                }
            }
        }
    }

    /**
     * Updates the tuner configuration with the current tuner PPM setting so that the value can be stored across
     * sessions.
     * @param discoveredTuner that has an updated PPM value.
     */
    public void updateTunerPPM(DiscoveredTuner discoveredTuner)
    {
        if(discoveredTuner != null)
        {
            TunerType tunerType = discoveredTuner.getTuner().getTunerType();

            //See note in tunerStatusUpdated()
            if(tunerType != TunerType.RECORDING && tunerType != TunerType.RTL_TCP)
            {
                TunerConfiguration tunerConfiguration = getTunerConfiguration(tunerType, discoveredTuner.getId());

                if(tunerConfiguration != null)
                {
                    tunerConfiguration.setFrequencyCorrection(discoveredTuner.getTuner().getTunerController().getFrequencyCorrection());
                    saveConfigurations();
                }
            }
        }
    }

    /**
     * Adds the discovered tuner to the list of disabled tuners
     */
    private void addDisabledTuner(DiscoveredTuner discoveredTuner)
    {
        if(!isDisabled(discoveredTuner))
        {
            mLock.lock();

            try
            {
                mDisabledTunerList.add(new DisabledTuner(discoveredTuner.getTunerClass(), discoveredTuner.getId()));
            }
            finally
            {
                mLock.unlock();
            }

            saveConfigurations();
        }
    }

    /**
     * Removes the tuner from the disabled tuners list
     * @param discoveredTuner to remove
     */
    private void removeDisabledTuner(DiscoveredTuner discoveredTuner)
    {
        mLock.lock();

        try
        {
            mDisabledTunerList.removeIf(tuner -> tuner.matches(discoveredTuner));
        }
        finally
        {
            mLock.unlock();
        }

        saveConfigurations();
    }

    /**
     * Indicates if the discovered tuner is disabled.  This method should only be used to determine the disabled state
     * of a tuner when it is added to the system for use, such as tuner discovery at application startup, or for
     * USB tuner hotplug device add notifications.
     *
     * @param discoveredTuner to check for disabled status.
     * @return true if the tuner is supposed to be disabled.
     */
    public boolean isDisabled(DiscoveredTuner discoveredTuner)
    {
        return findDisabledTuner(discoveredTuner) != null;
    }

    /**
     * Finds the disabled tuner that matches the discovered tuner.
     * @param discoveredTuner to search for
     * @return disabled tuner instance or null if the discovered tuner is not currently disabled.
     */
    private DisabledTuner findDisabledTuner(DiscoveredTuner discoveredTuner)
    {
        DisabledTuner found = null;

        mLock.lock();

        try
        {
            for(DisabledTuner disabledTuner: mDisabledTunerList)
            {
                if(disabledTuner.matches(discoveredTuner))
                {
                    found = disabledTuner;
                    break;
                }
            }
        }
        finally
        {
            mLock.unlock();
        }

        return found;
    }

    /**
     * Adds the tuner configuration if one doesn't exist that matches the tuner type and unique id.
     * @param tunerConfiguration to add
     */
    public void addTunerConfiguration(TunerConfiguration tunerConfiguration)
    {
        if(!mTunerConfigurations.stream().filter(config -> config.getTunerType().equals(tunerConfiguration.getTunerType()) &&
                config.getUniqueID().equalsIgnoreCase(tunerConfiguration.getUniqueID())).findFirst().isPresent())
        {
            mTunerConfigurations.add(tunerConfiguration);
            saveConfigurations();
        }
    }

    /**
     * Removes the tuner configuration from this manager.
     * @param tunerConfiguration to remove
     */
    public void removeTunerConfiguration(TunerConfiguration tunerConfiguration)
    {
        mTunerConfigurations.remove(tunerConfiguration);
        saveConfigurations();
    }

    /**
     * Provides an existing or creates a new tuner configuration for the specified tuner type and unique ID value.
     *
     * Note: this method is not thread-safe.
     */
    public TunerConfiguration getTunerConfiguration(TunerType type, String uniqueID )
    {
        Optional<TunerConfiguration> optional = mTunerConfigurations.stream().filter(config -> config.getTunerType().equals(type) &&
                config.getUniqueID().equalsIgnoreCase(uniqueID)).findFirst();

        if(optional.isPresent())
        {
            return optional.get();
        }

        TunerConfiguration config = TunerFactory.getTunerConfiguration(type, uniqueID);
        addTunerConfiguration(config);
        return config;
    }

    /**
     * Get all tuner configurations that match the specified tuner type.
     * @param tunerType to match
     * @return list of all configurations.
     */
    public List<TunerConfiguration> getTunerConfigurations(TunerType tunerType)
    {
        return mTunerConfigurations.stream().filter(tunerConfiguration -> tunerConfiguration.getTunerType()
                .equals(tunerType)).toList();
    }

    /**
     * Saves the current tuner configuration state and resets the save pending flag
     */
    public class ConfigurationSaveTask implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                save();
            }
            catch(Exception e)
            {
                mLog.error("Error saving tuner configurations", e);
            }

            mSavePending.set(false);
        }
    }
}