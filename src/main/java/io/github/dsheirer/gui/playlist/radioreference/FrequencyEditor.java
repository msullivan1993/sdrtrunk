/*
 * *****************************************************************************
 * Copyright (C) 2014-2026 Dennis Sheirer
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

package io.github.dsheirer.gui.playlist.radioreference;

import com.google.common.eventbus.Subscribe;
import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.AliasModel;
import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.eventbus.MyEventBus;
import io.github.dsheirer.gui.control.MaxLengthUnaryOperator;
import io.github.dsheirer.gui.playlist.channel.ViewChannelRequest;
import io.github.dsheirer.module.decode.DecoderFactory;
import io.github.dsheirer.module.decode.analog.DecodeConfigAnalog;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.module.decode.nbfm.DecodeConfigNBFM;
import io.github.dsheirer.module.decode.nxdn.DecodeConfigNXDN;
import io.github.dsheirer.module.decode.nxdn.layer3.type.TransmissionMode;
import io.github.dsheirer.module.decode.p25.phase1.DecodeConfigP25Phase1;
import io.github.dsheirer.module.decode.p25.phase1.Modulation;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.protocol.Protocol;
import io.github.dsheirer.settings.Settings;
import io.github.dsheirer.rrapi.type.Category;
import io.github.dsheirer.rrapi.type.Frequency;
import io.github.dsheirer.rrapi.type.Mode;
import io.github.dsheirer.rrapi.type.SubCategory;
import io.github.dsheirer.service.radioreference.RadioReference;
import io.github.dsheirer.settings.SettingsType;
import io.github.dsheirer.source.config.SourceConfigTuner;
import io.github.dsheirer.util.ThreadPool;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrequencyEditor extends VBox
{
    private static final Logger mLog = LoggerFactory.getLogger(FrequencyEditor.class);
    private final DecimalFormat FREQUENCY_FORMATTER = new DecimalFormat("0.00000");
    private Settings mSettings;
    private RadioReference mRadioReference;
    private PlaylistManager mPlaylistManager;
    private Level mLevel;
    private TextField mAlphaTagTextField;
    private TextField mFrequencyTextField;
    private TextField mModeTextField;
    private TextField mToneTextField;
    private TextField mSystemTextField;
    private TextField mSiteTextField;
    private TextField mNameTextField;
    private TextField mDecoderTextField;
    private Button mCreateButton;
    private Label mChannelCreatedLabel;
    private CheckBox mShowCreatedChannelCheckBox;
    private CheckBox mAutoStartCheckBox;
    private ModeDecoderType mModeDecoderType;
    private long mFrequency;
    private static final String NO_ALIAS_LIST = "(None)";
    private static final String NO_BROADCAST_CHANNEL = "(None)";
    private ComboBox<String> mAliasListComboBox;
    private ObservableList<String> mAliasListNames = FXCollections.observableArrayList();
    private Button mNewAliasListButton;
    private Button mRenameAliasListButton;
    private CheckBox mCreateAliasCheckBox;
    private TextField mTalkgroupTextField;
    private TextField mAliasNameTextField;
    private TextField mAliasGroupTextField;
    private TextField mStreamAsTalkgroupTextField;
    private ComboBox<String> mBroadcastChannelComboBox;
    private Label mAliasWarningLabel;
    private Label mTalkgroupLabel;
    private PauseTransition mStatusTransition;
    private boolean mStatusShowing = false;
    private static final String WARNING_STYLE = "-fx-text-fill: #d2691e;";
    private static final String SUCCESS_STYLE = "-fx-text-fill: #2e8b57; -fx-font-weight: bold;";

    /**
     * User choices when the talkgroup is already aliased in the selected alias list
     */
    private enum ExistingAliasChoice {NEW_NUMBERED_LIST, NEW_CHANNEL_LIST, CREATE_ANYWAY, CHANNEL_ONLY, CANCEL}

    /**
     * User choices when a P25 or NXDN conventional talkgroup isn't the conventional default
     */
    private enum DefaultTalkgroupChoice {USE_DEFAULT, KEEP, CANCEL}

    private CheckBox mOwnAliasListCheckBox;

    public FrequencyEditor(Settings settings, RadioReference radioReference,
                           PlaylistManager playlistManager, Level level)
    {
        mSettings = settings;
        mRadioReference = radioReference;
        mPlaylistManager = playlistManager;
        mLevel = level;
        MyEventBus.getGlobalEventBus().register(this);

        GridPane gridPane = new GridPane();
        gridPane.setVgap(5);
        gridPane.setHgap(5);

        int row = 0;

        Label detailsLabel = new Label("Radio Reference Details");
        GridPane.setConstraints(detailsLabel, 1, row);
        gridPane.getChildren().add(detailsLabel);

        Label alphaLabel = new Label("Alpha Tag");
        alphaLabel.setLabelFor(getAlphaTagTextField());
        GridPane.setHalignment(alphaLabel, HPos.RIGHT);
        GridPane.setConstraints(alphaLabel, 0, ++row);
        gridPane.getChildren().add(alphaLabel);

        GridPane.setHgrow(getAlphaTagTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getAlphaTagTextField(), 1, row);
        gridPane.getChildren().add(getAlphaTagTextField());

        Label toneLabel = new Label("Tone");
        toneLabel.setLabelFor(getToneTextField());
        GridPane.setHalignment(toneLabel, HPos.RIGHT);
        GridPane.setConstraints(toneLabel, 0, ++row);
        gridPane.getChildren().add(toneLabel);

        GridPane.setHgrow(getToneTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getToneTextField(), 1, row);
        gridPane.getChildren().add(getToneTextField());

        Label modeLabel = new Label("Mode");
        modeLabel.setLabelFor(getModeTextField());
        GridPane.setHalignment(modeLabel, HPos.RIGHT);
        GridPane.setConstraints(modeLabel, 0, ++row);
        gridPane.getChildren().add(modeLabel);

        GridPane.setHgrow(getModeTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getModeTextField(), 1, row);
        gridPane.getChildren().add(getModeTextField());

        Label frequencyLabel = new Label("Frequency");
        frequencyLabel.setLabelFor(getFrequencyTextField());
        GridPane.setHalignment(frequencyLabel, HPos.RIGHT);
        GridPane.setConstraints(frequencyLabel, 0, ++row);
        gridPane.getChildren().add(frequencyLabel);

        GridPane.setHgrow(getFrequencyTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getFrequencyTextField(), 1, row);
        gridPane.getChildren().add(getFrequencyTextField());

        Separator separator = new Separator(Orientation.HORIZONTAL);
        GridPane.setConstraints(separator, 0, ++row, 2, 1);
        gridPane.getChildren().add(separator);

        Label createLabel = new Label("Create Channel Configuration");
        GridPane.setConstraints(createLabel, 1, ++row);
        gridPane.getChildren().add(createLabel);

        Label aliasListLabel = new Label("Alias List");
        aliasListLabel.setLabelFor(getAliasListComboBox());
        GridPane.setHalignment(aliasListLabel, HPos.RIGHT);
        GridPane.setConstraints(aliasListLabel, 0, ++row);
        gridPane.getChildren().add(aliasListLabel);

        HBox aliasListBox = new HBox(5, getAliasListComboBox(), getNewAliasListButton(), getRenameAliasListButton());
        HBox.setHgrow(getAliasListComboBox(), Priority.ALWAYS);
        GridPane.setHgrow(aliasListBox, Priority.ALWAYS);
        GridPane.setConstraints(aliasListBox, 1, row);
        gridPane.getChildren().add(aliasListBox);

        GridPane.setConstraints(getOwnAliasListCheckBox(), 1, ++row);
        gridPane.getChildren().add(getOwnAliasListCheckBox());

        Label systemLabel = new Label("System");
        systemLabel.setLabelFor(getSystemTextField());
        GridPane.setHalignment(systemLabel, HPos.RIGHT);
        GridPane.setConstraints(systemLabel, 0, ++row);
        gridPane.getChildren().add(systemLabel);

        GridPane.setHgrow(getSystemTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getSystemTextField(), 1, row);
        gridPane.getChildren().add(getSystemTextField());

        Label siteLabel = new Label("Site");
        siteLabel.setLabelFor(getSiteTextField());
        GridPane.setHalignment(siteLabel, HPos.RIGHT);
        GridPane.setConstraints(siteLabel, 0, ++row);
        gridPane.getChildren().add(siteLabel);

        GridPane.setHgrow(getSiteTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getSiteTextField(), 1, row);
        gridPane.getChildren().add(getSiteTextField());

        Label nameLabel = new Label("Name");
        nameLabel.setLabelFor(getNameTextField());
        GridPane.setHalignment(nameLabel, HPos.RIGHT);
        GridPane.setConstraints(nameLabel, 0, ++row);
        gridPane.getChildren().add(nameLabel);

        GridPane.setHgrow(getNameTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getNameTextField(), 1, row);
        gridPane.getChildren().add(getNameTextField());

        Label decoderLabel = new Label("Decoder");
        decoderLabel.setLabelFor(getDecoderTextField());
        GridPane.setHalignment(decoderLabel, HPos.RIGHT);
        GridPane.setConstraints(decoderLabel, 0, ++row);
        gridPane.getChildren().add(decoderLabel);

        GridPane.setHgrow(getDecoderTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getDecoderTextField(), 1, row);
        gridPane.getChildren().add(getDecoderTextField());

        Separator aliasSeparator = new Separator(Orientation.HORIZONTAL);
        GridPane.setConstraints(aliasSeparator, 0, ++row, 2, 1);
        gridPane.getChildren().add(aliasSeparator);

        GridPane.setConstraints(getCreateAliasCheckBox(), 1, ++row);
        gridPane.getChildren().add(getCreateAliasCheckBox());

        mTalkgroupLabel = new Label("Talkgroup");
        mTalkgroupLabel.setLabelFor(getTalkgroupTextField());
        GridPane.setHalignment(mTalkgroupLabel, HPos.RIGHT);
        GridPane.setConstraints(mTalkgroupLabel, 0, ++row);
        gridPane.getChildren().add(mTalkgroupLabel);
        GridPane.setHgrow(getTalkgroupTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getTalkgroupTextField(), 1, row);
        gridPane.getChildren().add(getTalkgroupTextField());

        Label aliasNameLabel = new Label("Alias Name");
        aliasNameLabel.setLabelFor(getAliasNameTextField());
        GridPane.setHalignment(aliasNameLabel, HPos.RIGHT);
        GridPane.setConstraints(aliasNameLabel, 0, ++row);
        gridPane.getChildren().add(aliasNameLabel);
        GridPane.setHgrow(getAliasNameTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getAliasNameTextField(), 1, row);
        gridPane.getChildren().add(getAliasNameTextField());

        Label aliasGroupLabel = new Label("Alias Group");
        aliasGroupLabel.setLabelFor(getAliasGroupTextField());
        GridPane.setHalignment(aliasGroupLabel, HPos.RIGHT);
        GridPane.setConstraints(aliasGroupLabel, 0, ++row);
        gridPane.getChildren().add(aliasGroupLabel);
        GridPane.setHgrow(getAliasGroupTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getAliasGroupTextField(), 1, row);
        gridPane.getChildren().add(getAliasGroupTextField());

        Label streamAsLabel = new Label("Stream As TG");
        streamAsLabel.setLabelFor(getStreamAsTalkgroupTextField());
        GridPane.setHalignment(streamAsLabel, HPos.RIGHT);
        GridPane.setConstraints(streamAsLabel, 0, ++row);
        gridPane.getChildren().add(streamAsLabel);
        GridPane.setHgrow(getStreamAsTalkgroupTextField(), Priority.ALWAYS);
        GridPane.setConstraints(getStreamAsTalkgroupTextField(), 1, row);
        gridPane.getChildren().add(getStreamAsTalkgroupTextField());

        Label streamToLabel = new Label("Stream To");
        streamToLabel.setLabelFor(getBroadcastChannelComboBox());
        GridPane.setHalignment(streamToLabel, HPos.RIGHT);
        GridPane.setConstraints(streamToLabel, 0, ++row);
        gridPane.getChildren().add(streamToLabel);
        GridPane.setHgrow(getBroadcastChannelComboBox(), Priority.ALWAYS);
        GridPane.setConstraints(getBroadcastChannelComboBox(), 1, row);
        gridPane.getChildren().add(getBroadcastChannelComboBox());

        GridPane.setConstraints(getAliasWarningLabel(), 1, ++row);
        gridPane.getChildren().add(getAliasWarningLabel());

        GridPane.setConstraints(getAutoStartCheckBox(), 1, ++row);
        gridPane.getChildren().add(getAutoStartCheckBox());

        GridPane.setConstraints(getShowCreatedChannelCheckBox(), 1, ++row);
        gridPane.getChildren().add(getShowCreatedChannelCheckBox());

        GridPane.setHgrow(getCreateButton(), Priority.ALWAYS);
        GridPane.setMargin(getCreateButton(), new Insets(10,0,0,0));
        GridPane.setConstraints(getCreateButton(), 1, ++row);
        gridPane.getChildren().add(getCreateButton());

        GridPane.setConstraints(getChannelCreatedLabel(), 1, ++row);
        gridPane.getChildren().add(getChannelCreatedLabel());

        getChildren().add(gridPane);
    }

    public void setItem(Frequency item, Category category, SubCategory subCategory)
    {
        mModeDecoderType = ModeDecoderType.UNKNOWN;

        if(item != null)
        {
            mFrequency = item.getDownlinkHz();
            getAlphaTagTextField().setText(item.getAlphaTag());
            getFrequencyTextField().setText(FREQUENCY_FORMATTER.format(item.getDownlink()));
            getToneTextField().setText(RadioReferenceTone.getToneField(item));
            updateMode(item.getMode());
            getSystemTextField().setText(category != null ? category.getName() : null);
            getSiteTextField().setText(subCategory != null ? subCategory.getName() : null);
            getNameTextField().setText(item.getAlphaTag());
            String aliasName = item.getAlphaTag();
            if(aliasName == null || aliasName.isBlank())
            {
                aliasName = item.getDescription();
            }
            getAliasNameTextField().setText(aliasName);
            getAliasGroupTextField().setText(category != null ? category.getName() : null);
            getStreamAsTalkgroupTextField().setText(null);
        }
        else
        {
            mFrequency = 0;
            getAlphaTagTextField().setText(null);
            getFrequencyTextField().setText(null);
            getModeTextField().setText(null);
            getToneTextField().setText(null);
            getSystemTextField().setText(null);
            getSiteTextField().setText(null);
            getNameTextField().setText(null);
            getAliasNameTextField().setText(null);
            getAliasGroupTextField().setText(null);
            getStreamAsTalkgroupTextField().setText(null);
            getTalkgroupTextField().setText(null);

            getSystemTextField().setDisable(true);
            getSiteTextField().setDisable(true);
            getNameTextField().setDisable(true);
            getCreateButton().setDisable(true);
            getShowCreatedChannelCheckBox().setDisable(true);
            getAutoStartCheckBox().setDisable(true);
        }

        updateAliasControls(true);
    }

    /**
     * Updates the mode text field and related controls to indicate if a channel configuration can be created.
     */
    private void updateMode(String modeId)
    {
        if(modeId != null)
        {
            ThreadPool.CACHED.execute(() -> {
                Integer parsed = null;

                try
                {
                    parsed = Integer.parseInt(modeId);
                }
                catch(Exception e)
                {
                    //Do nothing, we couldn't parse the value
                }

                if(parsed != null)
                {
                    try
                    {
                        Mode mode = mRadioReference.getService().getMode(parsed);
                        mModeDecoderType = ModeDecoderType.get(mode);

                        Platform.runLater(() -> {
                            getModeTextField().setText(mode.getName());

                            boolean disable = !mModeDecoderType.hasDecoderType();

                            getSystemTextField().setDisable(disable);
                            getSiteTextField().setDisable(disable);
                            getNameTextField().setDisable(disable);
                            getCreateButton().setDisable(disable);
                            getShowCreatedChannelCheckBox().setDisable(disable);
                            getAutoStartCheckBox().setDisable(disable);

                            updateAliasControls(true);

                            if(mModeDecoderType.hasDecoderType())
                            {
                                getDecoderTextField().setText(mModeDecoderType.getDecoderType().getDisplayString());
                            }
                            else
                            {
                                getDecoderTextField().setText(mode.getName() + " - Not Supported");
                            }
                        });
                    }
                    catch(Throwable t)
                    {
                        mLog.error("Error retrieving mode from radio reference", t);
                    }
                }
            });
        }
        else
        {
            getModeTextField().setText(null);
        }
    }

    private TextField getAlphaTagTextField()
    {
        if(mAlphaTagTextField == null)
        {
            mAlphaTagTextField = new TextField();
            mAlphaTagTextField.setMaxWidth(Double.MAX_VALUE);
            mAlphaTagTextField.setDisable(true);
        }

        return mAlphaTagTextField;
    }

    private TextField getFrequencyTextField()
    {
        if(mFrequencyTextField == null)
        {
            mFrequencyTextField = new TextField();
            mFrequencyTextField.setMaxWidth(Double.MAX_VALUE);
            mFrequencyTextField.setDisable(true);
        }

        return mFrequencyTextField;
    }

    private TextField getModeTextField()
    {
        if(mModeTextField == null)
        {
            mModeTextField = new TextField();
            mModeTextField.setMaxWidth(Double.MAX_VALUE);
            mModeTextField.setDisable(true);
        }

        return mModeTextField;
    }

    private TextField getToneTextField()
    {
        if(mToneTextField == null)
        {
            mToneTextField = new TextField();
            mToneTextField.setMaxWidth(Double.MAX_VALUE);
            mToneTextField.setDisable(true);
        }

        return mToneTextField;
    }

    private CheckBox getShowCreatedChannelCheckBox()
    {
        if(mShowCreatedChannelCheckBox == null)
        {
            boolean show = mSettings.getRadioReferenceSettings().getShowChannelEditor(mLevel);
            mShowCreatedChannelCheckBox = new CheckBox("View Channel Editor After Create");
            mShowCreatedChannelCheckBox.setDisable(true);
            mShowCreatedChannelCheckBox.selectedProperty().set(show);
            mShowCreatedChannelCheckBox.selectedProperty()
                .addListener((observable, oldValue, newValue) -> mSettings.getRadioReferenceSettings()
                    .setShowChannelEditor(newValue, mLevel));
        }

        return mShowCreatedChannelCheckBox;
    }

    private CheckBox getAutoStartCheckBox()
    {
        if(mAutoStartCheckBox == null)
        {
            mAutoStartCheckBox = new CheckBox("Auto-Start Channel");
            mAutoStartCheckBox.setDisable(true);
            mAutoStartCheckBox.setTooltip(new Tooltip("Start the created channel automatically when sdrtrunk starts"));
            mAutoStartCheckBox.setSelected(mSettings.getRadioReferenceSettings().isAutoStartImportedChannels());
            mAutoStartCheckBox.selectedProperty().addListener((observable, oldValue, selected) ->
                mSettings.getRadioReferenceSettings().setAutoStartImportedChannels(selected));
        }

        return mAutoStartCheckBox;
    }

    private Label getChannelCreatedLabel()
    {
        if(mChannelCreatedLabel == null)
        {
            mChannelCreatedLabel = new Label("Channel Created Successfully");
            mChannelCreatedLabel.setDisable(true);
            mChannelCreatedLabel.setOpacity(0);
        }

        return mChannelCreatedLabel;
    }

    private Button getCreateButton()
    {
        if(mCreateButton == null)
        {
            mCreateButton = new Button("Create");
            mCreateButton.setDisable(true);
            mCreateButton.setOnAction(event -> createChannelAndAlias());
        }

        return mCreateButton;
    }

    /**
     * Creates the channel configuration and, when an alias list applies, assigns the alias list to the channel and
     * creates the talkgroup alias.
     */
    private void createChannelAndAlias()
    {
        boolean supported = mModeDecoderType != null && mModeDecoderType.hasDecoderType() && mFrequency > 0;
        Protocol protocol = supported ? RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType) : null;
        boolean channelAssigned = RadioReferenceAliasSupport.isChannelAssignedTalkgroup(protocol);
        boolean ownList = isOwnAliasList(protocol);
        String aliasListName = ownList ? getOwnAliasListName() : getSelectedAliasListName();
        boolean createAlias = aliasListName != null && protocol != null && getCreateAliasCheckBox().isSelected();
        Integer talkgroup = RadioReferenceAliasSupport.parseTalkgroup(getTalkgroupTextField().getText(), protocol);

        if((channelAssigned || createAlias) && talkgroup == null)
        {
            showError("Invalid Talkgroup", "Talkgroup must be a value between " +
                RadioReferenceAliasSupport.getTalkgroupRange(protocol) + ".");
            return;
        }

        Integer streamAs = null;

        if(createAlias)
        {
            String streamAsText = getStreamAsTalkgroupTextField().getText();
            streamAs = RadioReferenceAliasSupport.parseTalkgroup(streamAsText);

            if(streamAsText != null && !streamAsText.isBlank() && streamAs == null)
            {
                showError("Invalid Stream As Talkgroup", "Stream As Talkgroup must be blank or a value between 1 and 65,535.");
                return;
            }

            Integer conventionalDefault = RadioReferenceAliasSupport.getConventionalDefaultTalkgroup(protocol);

            if(conventionalDefault != null && talkgroup.intValue() != conventionalDefault &&
                mSettings.getRadioReferenceSettings().isConventionalDefaultTalkgroupWarning())
            {
                switch(confirmNonDefaultTalkgroup(protocol, talkgroup, conventionalDefault, streamAs))
                {
                    case CANCEL:
                        return;
                    case USE_DEFAULT:
                        if(streamAs == null && talkgroup >= RadioReferenceAliasSupport.MIN_TALKGROUP)
                        {
                            streamAs = talkgroup;
                            getStreamAsTalkgroupTextField().setText(String.valueOf(streamAs));
                        }
                        talkgroup = conventionalDefault;
                        getTalkgroupTextField().setText(String.valueOf(talkgroup));
                        break;
                    default:
                        break;
                }
            }

            if(!ownList)
            {
                Alias existing = RadioReferenceAliasSupport.findExisting(protocol, talkgroup,
                    getAliasListAliases(aliasListName));

                if(existing != null)
                {
                    List<String> names = mPlaylistManager.getAliasModel().aliasListNames();
                    String numberedListName = RadioReferenceAliasSupport.getNextAliasListName(aliasListName, names);
                    String channelListName = RadioReferenceAliasSupport.getUniqueAliasListName(getOwnAliasListSource(), names);

                    switch(confirmExisting(protocol, talkgroup, existing, aliasListName, numberedListName, channelListName))
                    {
                        case CANCEL:
                            return;
                        case CHANNEL_ONLY:
                            createAlias = false;
                            break;
                        case NEW_NUMBERED_LIST:
                            aliasListName = numberedListName;
                            selectNewAliasList(aliasListName);
                            break;
                        case NEW_CHANNEL_LIST:
                            aliasListName = channelListName;
                            selectNewAliasList(aliasListName);
                            break;
                        default:
                            break;
                    }
                }
            }
        }

        Channel channel = createChannel(mModeDecoderType, mFrequency, getSystemTextField().getText(),
            getSiteTextField().getText(), getNameTextField().getText(), getToneTextField().getText());

        if(channel == null)
        {
            return;
        }

        channel.setAutoStart(getAutoStartCheckBox().isSelected());

        if(channelAssigned && channel.getDecodeConfiguration() instanceof DecodeConfigAnalog analog)
        {
            analog.setTalkgroup(talkgroup);
        }

        Alias alias = null;

        if(aliasListName != null)
        {
            mPlaylistManager.getAliasModel().addAliasList(aliasListName);
            channel.setAliasListName(aliasListName);

            if(createAlias)
            {
                String aliasName = getAliasNameTextField().getText();

                if(aliasName == null || aliasName.isBlank())
                {
                    aliasName = getNameTextField().getText();
                }

                alias = RadioReferenceAliasSupport.createAlias(aliasName, aliasListName,
                    getAliasGroupTextField().getText(), protocol, talkgroup, streamAs, getSelectedBroadcastChannel());
            }
        }

        getCreateButton().setDisable(true);
        mPlaylistManager.getChannelModel().addChannel(channel);

        if(alias != null)
        {
            mPlaylistManager.getAliasModel().addAlias(alias);
        }

        updateAliasControls(true);

        StringBuilder status = new StringBuilder();
        status.append("Channel created: ").append(channel.getName());

        if(alias != null)
        {
            status.append("\nAlias '").append(alias.getName()).append("' (TG ").append(talkgroup);

            if(streamAs != null)
            {
                status.append(", Stream As ").append(streamAs);
            }

            status.append(") added to alias list '").append(aliasListName).append("'");
        }
        else if(aliasListName != null)
        {
            status.append("\nAlias list '").append(aliasListName).append("' assigned");
        }

        showStatus(status.toString());

        if(getShowCreatedChannelCheckBox().selectedProperty().get())
        {
            MyEventBus.getGlobalEventBus().post(new ViewChannelRequest(channel));
        }
    }

    /**
     * Adds a new alias list and selects it so that the next import continues in that list.
     */
    private void selectNewAliasList(String aliasListName)
    {
        mPlaylistManager.getAliasModel().addAliasList(aliasListName);
        refreshAliasListNames();
        getAliasListComboBox().getSelectionModel().select(aliasListName);
    }

    /**
     * Indicates if the channel gets its own alias list: P25/NXDN conventional with the option enabled.
     */
    private boolean isOwnAliasList(Protocol protocol)
    {
        return RadioReferenceAliasSupport.isDecodedTalkgroup(protocol) && getOwnAliasListCheckBox().isSelected();
    }

    /**
     * Name used for a channel's own alias list: the channel name, or the alias name when the channel name is blank.
     */
    private String getOwnAliasListSource()
    {
        String name = getNameTextField().getText();

        if(name == null || name.isBlank())
        {
            name = getAliasNameTextField().getText();
        }

        return name;
    }

    /**
     * Unique alias list name for a channel that gets its own alias list
     */
    private String getOwnAliasListName()
    {
        return RadioReferenceAliasSupport.getUniqueAliasListName(getOwnAliasListSource(),
            mPlaylistManager.getAliasModel().aliasListNames());
    }

    private CheckBox getOwnAliasListCheckBox()
    {
        if(mOwnAliasListCheckBox == null)
        {
            mOwnAliasListCheckBox = new CheckBox("Own alias list for this channel (named from channel name)");
            mOwnAliasListCheckBox.setTooltip(new Tooltip("P25 and NXDN conventional channels nearly all use the same " +
                "talkgroup, so each channel needs its own alias list for its alias and streaming to match."));
            mOwnAliasListCheckBox.setSelected(mSettings.getRadioReferenceSettings().isConventionalOwnAliasList());
            mOwnAliasListCheckBox.setVisible(false);
            mOwnAliasListCheckBox.setManaged(false);
            mOwnAliasListCheckBox.selectedProperty().addListener((observable, oldValue, selected) -> {
                mSettings.getRadioReferenceSettings().setConventionalOwnAliasList(selected);
                updateAliasControls(false);
            });
        }

        return mOwnAliasListCheckBox;
    }

    /**
     * Creates a channel configuration from the supplied values
     * @param modeDecoderType indicating the decoder type
     * @param frequency value
     * @param system value
     * @param site value
     * @param name value
     * @return configured channel or null
     */
    private Channel createChannel(ModeDecoderType modeDecoderType, long frequency, String system, String site, String name,
                                  String tone)
    {
        return RadioReferenceChannelFactory.conventional(modeDecoderType, frequency, system, site, name, tone);
    }

    private TextField getSystemTextField()
    {
        if(mSystemTextField == null)
        {
            mSystemTextField = new TextField();
            mSystemTextField.setMaxWidth(Double.MAX_VALUE);
            mSystemTextField.setDisable(true);
        }

        return mSystemTextField;
    }

    private TextField getSiteTextField()
    {
        if(mSiteTextField == null)
        {
            mSiteTextField = new TextField();
            mSiteTextField.setMaxWidth(Double.MAX_VALUE);
            mSiteTextField.setDisable(true);
        }

        return mSiteTextField;
    }

    private TextField getNameTextField()
    {
        if(mNameTextField == null)
        {
            mNameTextField = new TextField();
            mNameTextField.setMaxWidth(Double.MAX_VALUE);
            mNameTextField.setDisable(true);
            mNameTextField.textProperty().addListener((observable, oldValue, newValue) -> updateAliasWarning());
        }

        return mNameTextField;
    }

    private TextField getDecoderTextField()
    {
        if(mDecoderTextField == null)
        {
            mDecoderTextField = new TextField();
            mDecoderTextField.setMaxWidth(Double.MAX_VALUE);
            mDecoderTextField.setDisable(true);
        }

        return mDecoderTextField;
    }

    /**
     * Alias list selector.  The selection is retained across frequency selections so that a batch of channels can be
     * imported into the same alias list.
     */
    private ComboBox<String> getAliasListComboBox()
    {
        if(mAliasListComboBox == null)
        {
            mAliasListComboBox = new ComboBox<>(mAliasListNames);
            mAliasListComboBox.setMaxWidth(Double.MAX_VALUE);
            mAliasListComboBox.setTooltip(new Tooltip("Alias list to assign to the created channel"));
            refreshAliasListNames();
            mAliasListComboBox.getSelectionModel().select(NO_ALIAS_LIST);
            mPlaylistManager.getAliasModel().aliasListNames()
                .addListener((ListChangeListener<String>)change -> {
                    if(Platform.isFxApplicationThread())
                    {
                        refreshAliasListNames();
                    }
                    else
                    {
                        Platform.runLater(this::refreshAliasListNames);
                    }
                });
            mAliasListComboBox.setOnAction(event -> updateAliasControls(true));
        }

        return mAliasListComboBox;
    }

    /**
     * Synchronizes the alias list names with the alias model, preserving the current selection.
     */
    private void refreshAliasListNames()
    {
        String selected = mAliasListComboBox != null ? mAliasListComboBox.getSelectionModel().getSelectedItem() : null;

        List<String> names = new ArrayList<>();
        names.add(NO_ALIAS_LIST);
        mPlaylistManager.getAliasModel().aliasListNames().stream()
            .filter(name -> name != null && !name.isEmpty() && !name.contentEquals(AliasModel.NO_ALIAS_LIST))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(names::add);

        if(names.equals(mAliasListNames))
        {
            return;
        }

        mAliasListNames.setAll(names);

        if(mAliasListComboBox != null)
        {
            mAliasListComboBox.getSelectionModel().select(selected != null && mAliasListNames.contains(selected) ?
                selected : NO_ALIAS_LIST);
        }
    }

    /**
     * Currently selected alias list name or null when no alias list is selected
     */
    private String getSelectedAliasListName()
    {
        String selected = getAliasListComboBox().getSelectionModel().getSelectedItem();
        return (selected == null || selected.contentEquals(NO_ALIAS_LIST)) ? null : selected;
    }

    private Button getNewAliasListButton()
    {
        if(mNewAliasListButton == null)
        {
            mNewAliasListButton = new Button("New...");
            mNewAliasListButton.setTooltip(new Tooltip("Create a new alias list"));
            mNewAliasListButton.setOnAction(event -> {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Create New Alias List");
                dialog.setHeaderText("Please enter an alias list name (max 25 chars).");
                dialog.setContentText("Name:");
                dialog.getEditor().setTextFormatter(new TextFormatter<String>(new MaxLengthUnaryOperator(25)));
                dialog.initOwner(getNewAliasListButton().getScene().getWindow());
                Optional<String> result = dialog.showAndWait();

                result.ifPresent(name -> {
                    if(name != null && !name.isBlank())
                    {
                        String trimmed = name.trim();
                        mPlaylistManager.getAliasModel().addAliasList(trimmed);
                        refreshAliasListNames();
                        getAliasListComboBox().getSelectionModel().select(trimmed);
                        updateAliasControls(true);
                    }
                });
            });
        }

        return mNewAliasListButton;
    }

    private Button getRenameAliasListButton()
    {
        if(mRenameAliasListButton == null)
        {
            mRenameAliasListButton = new Button("Rename...");
            mRenameAliasListButton.setTooltip(new Tooltip("Rename the selected alias list across all aliases and channels"));
            mRenameAliasListButton.setDisable(true);
            mRenameAliasListButton.setOnAction(event -> {
                String aliasListName = getSelectedAliasListName();

                if(aliasListName == null)
                {
                    return;
                }

                TextInputDialog dialog = new TextInputDialog(aliasListName);
                dialog.setTitle("Renaming Alias List: " + aliasListName);
                dialog.setHeaderText("Please enter the new alias list name (max 25 chars).");
                dialog.setContentText("Name:");
                dialog.getEditor().setTextFormatter(new TextFormatter<String>(new MaxLengthUnaryOperator(25)));
                dialog.initOwner(getRenameAliasListButton().getScene().getWindow());
                Optional<String> result = dialog.showAndWait();

                result.ifPresent(name -> {
                    if(name != null && !name.isBlank() && !name.trim().equals(aliasListName))
                    {
                        String trimmed = name.trim();
                        mPlaylistManager.renameAliasList(aliasListName, trimmed);
                        refreshAliasListNames();
                        getAliasListComboBox().getSelectionModel().select(trimmed);
                        updateAliasControls(false);
                    }
                });
            });
        }

        return mRenameAliasListButton;
    }

    private CheckBox getCreateAliasCheckBox()
    {
        if(mCreateAliasCheckBox == null)
        {
            mCreateAliasCheckBox = new CheckBox("Create Alias In Alias List");
            mCreateAliasCheckBox.setSelected(true);
            mCreateAliasCheckBox.setDisable(true);
            mCreateAliasCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> updateAliasControls(false));
        }

        return mCreateAliasCheckBox;
    }

    private TextField getTalkgroupTextField()
    {
        if(mTalkgroupTextField == null)
        {
            mTalkgroupTextField = new TextField();
            mTalkgroupTextField.setMaxWidth(Double.MAX_VALUE);
            mTalkgroupTextField.setDisable(true);
            mTalkgroupTextField.setTooltip(new Tooltip("Analog: talkgroup assigned to the channel.  P25/NXDN: decoded talkgroup to alias (conventional default P25 1, NXDN 0)."));
            mTalkgroupTextField.textProperty().addListener((observable, oldValue, newValue) -> updateAliasWarning());
        }

        return mTalkgroupTextField;
    }

    private TextField getAliasNameTextField()
    {
        if(mAliasNameTextField == null)
        {
            mAliasNameTextField = new TextField();
            mAliasNameTextField.setMaxWidth(Double.MAX_VALUE);
            mAliasNameTextField.setDisable(true);
        }

        return mAliasNameTextField;
    }

    private TextField getAliasGroupTextField()
    {
        if(mAliasGroupTextField == null)
        {
            mAliasGroupTextField = new TextField();
            mAliasGroupTextField.setMaxWidth(Double.MAX_VALUE);
            mAliasGroupTextField.setDisable(true);
        }

        return mAliasGroupTextField;
    }

    private TextField getStreamAsTalkgroupTextField()
    {
        if(mStreamAsTalkgroupTextField == null)
        {
            mStreamAsTalkgroupTextField = new TextField();
            mStreamAsTalkgroupTextField.setMaxWidth(Double.MAX_VALUE);
            mStreamAsTalkgroupTextField.setDisable(true);
            mStreamAsTalkgroupTextField.setPromptText("Optional");
            mStreamAsTalkgroupTextField.setTooltip(new Tooltip("Optional talkgroup to use for streaming, to avoid " +
                "collisions when several channels share the same talkgroup (e.g. P25 conventional TG 1)"));
            mStreamAsTalkgroupTextField.textProperty().addListener((observable, oldValue, newValue) -> updateAliasWarning());
        }

        return mStreamAsTalkgroupTextField;
    }

    private ComboBox<String> getBroadcastChannelComboBox()
    {
        if(mBroadcastChannelComboBox == null)
        {
            mBroadcastChannelComboBox = new ComboBox<>();
            mBroadcastChannelComboBox.setMaxWidth(Double.MAX_VALUE);
            mBroadcastChannelComboBox.setDisable(true);
            mBroadcastChannelComboBox.setTooltip(new Tooltip("Optional streaming configuration for the created alias"));
            refreshBroadcastChannels();
            mBroadcastChannelComboBox.getSelectionModel().select(NO_BROADCAST_CHANNEL);
            mBroadcastChannelComboBox.setOnShowing(event -> refreshBroadcastChannels());
            mBroadcastChannelComboBox.setOnAction(event -> updateAliasWarning());
        }

        return mBroadcastChannelComboBox;
    }

    private void refreshBroadcastChannels()
    {
        String selected = mBroadcastChannelComboBox.getSelectionModel().getSelectedItem();
        List<String> names = mPlaylistManager.getBroadcastModel().getBroadcastConfigurationNames().stream()
            .filter(name -> name != null && !name.isEmpty())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();

        ObservableList<String> items = FXCollections.observableArrayList(names);
        items.add(0, NO_BROADCAST_CHANNEL);

        if(!items.equals(mBroadcastChannelComboBox.getItems()))
        {
            mBroadcastChannelComboBox.getItems().setAll(items);
        }

        mBroadcastChannelComboBox.getSelectionModel().select(selected != null && items.contains(selected) ?
            selected : NO_BROADCAST_CHANNEL);
    }

    private String getSelectedBroadcastChannel()
    {
        String selected = getBroadcastChannelComboBox().getSelectionModel().getSelectedItem();
        return (selected == null || selected.contentEquals(NO_BROADCAST_CHANNEL)) ? null : selected;
    }

    private Label getAliasWarningLabel()
    {
        if(mAliasWarningLabel == null)
        {
            mAliasWarningLabel = new Label();
            mAliasWarningLabel.setWrapText(true);
            mAliasWarningLabel.setMaxWidth(Double.MAX_VALUE);
            mAliasWarningLabel.setStyle(WARNING_STYLE);
        }

        return mAliasWarningLabel;
    }

    /**
     * Aliases in the named alias list
     */
    private List<Alias> getAliasListAliases(String aliasListName)
    {
        if(aliasListName == null)
        {
            return List.of();
        }

        return mPlaylistManager.getAliasModel().getAliases().stream()
            .filter(alias -> alias.hasList() && alias.getAliasListName().equalsIgnoreCase(aliasListName))
            .toList();
    }

    /**
     * Updates the enabled state of the alias controls for the current mode and alias list selection.
     * @param resetTalkgroup to replace the talkgroup value with the suggested value
     */
    private void updateAliasControls(boolean resetTalkgroup)
    {
        String selectedListName = getSelectedAliasListName();
        boolean supported = mModeDecoderType != null && mModeDecoderType.hasDecoderType() && mFrequency > 0;
        Protocol protocol = supported ? RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType) : null;
        boolean decoded = RadioReferenceAliasSupport.isDecodedTalkgroup(protocol);
        boolean ownList = isOwnAliasList(protocol);
        boolean hasList = ownList || selectedListName != null;
        boolean channelAssigned = RadioReferenceAliasSupport.isChannelAssignedTalkgroup(protocol);
        boolean createAlias = hasList && protocol != null && getCreateAliasCheckBox().isSelected();

        getOwnAliasListCheckBox().setVisible(decoded);
        getOwnAliasListCheckBox().setManaged(decoded);
        getAliasListComboBox().setDisable(ownList);
        getNewAliasListButton().setDisable(ownList);
        getRenameAliasListButton().setDisable(ownList || selectedListName == null);
        getCreateAliasCheckBox().setDisable(!hasList || protocol == null);
        getTalkgroupTextField().setDisable(protocol == null || !(channelAssigned || createAlias));
        getAliasNameTextField().setDisable(!createAlias);
        getAliasGroupTextField().setDisable(!createAlias);
        getStreamAsTalkgroupTextField().setDisable(!createAlias);
        getBroadcastChannelComboBox().setDisable(!createAlias);

        if(resetTalkgroup)
        {
            if(protocol != null)
            {
                int suggested = RadioReferenceAliasSupport.getSuggestedTalkgroup(protocol,
                    ownList ? List.<Alias>of() : getAliasListAliases(selectedListName));
                getTalkgroupTextField().setText(String.valueOf(suggested));
            }
            else
            {
                getTalkgroupTextField().setText(null);
            }
        }

        updateAliasWarning();
    }

    /**
     * Shows alias notes and warnings: the alias list a channel's own list will get, a talkgroup that's already aliased
     * in the alias list, a non-default P25/NXDN conventional talkgroup, a P25/NXDN conventional alias that streams
     * without a Stream As talkgroup, and a Stream As talkgroup that's already used.
     */
    private void updateAliasWarning()
    {
        if(mStatusShowing)
        {
            return;
        }

        List<String> lines = new ArrayList<>();
        boolean supported = mModeDecoderType != null && mModeDecoderType.hasDecoderType() && mFrequency > 0;
        Protocol protocol = supported ? RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType) : null;
        boolean ownList = isOwnAliasList(protocol);
        String aliasListName = ownList ? getOwnAliasListName() : getSelectedAliasListName();

        if(supported && aliasListName != null)
        {
            if(protocol == null)
            {
                lines.add("Alias list will be assigned to the channel. Create aliases for " +
                    mModeDecoderType.getDecoderType().getDisplayString() + " identifiers in the alias editor.");
            }
            else if(ownList && !getCreateAliasCheckBox().isSelected())
            {
                lines.add("Creates alias list '" + aliasListName + "' for this channel.");
            }
            else if(getCreateAliasCheckBox().isSelected())
            {
                String protocolLabel = protocol == Protocol.APCO25 ? "P25" : protocol.toString();
                Integer talkgroup = RadioReferenceAliasSupport.parseTalkgroup(getTalkgroupTextField().getText(), protocol);
                Integer conventionalDefault = RadioReferenceAliasSupport.getConventionalDefaultTalkgroup(protocol);

                if(ownList)
                {
                    lines.add("Creates alias list '" + aliasListName + "' for this channel.");
                }

                if(talkgroup == null)
                {
                    lines.add("Talkgroup must be a value between " + RadioReferenceAliasSupport.getTalkgroupRange(protocol) + ".");
                }
                else
                {
                    if(!ownList)
                    {
                        Alias existing = RadioReferenceAliasSupport.findExisting(protocol, talkgroup,
                            getAliasListAliases(aliasListName));

                        if(existing != null)
                        {
                            lines.add("Talkgroup " + talkgroup + " is already aliased as '" + existing.getName() +
                                "' in this alias list. Create will offer a new alias list for this channel.");
                        }
                    }

                    if(conventionalDefault != null && talkgroup.intValue() != conventionalDefault &&
                        mSettings.getRadioReferenceSettings().isConventionalDefaultTalkgroupWarning())
                    {
                        lines.add("TG " + talkgroup + " isn't the " + protocolLabel + " conventional default (" +
                            conventionalDefault + "). It's decoded off the air, so the alias only matches if the " +
                            "radios transmit TG " + talkgroup + ". Use Stream As TG to keep channels apart when streaming.");
                    }
                }

                Integer streamAs = RadioReferenceAliasSupport.parseTalkgroup(getStreamAsTalkgroupTextField().getText());
                String broadcastChannel = getSelectedBroadcastChannel();

                if(conventionalDefault != null && streamAs == null && broadcastChannel != null)
                {
                    lines.add("Other " + protocolLabel + " conventional channels streaming to '" + broadcastChannel +
                        "' will share TG " + conventionalDefault + ". Set Stream As TG to keep them apart.");
                }

                if(streamAs != null)
                {
                    Alias existingStream = RadioReferenceAliasSupport.findStreamAsTalkgroup(streamAs,
                        mPlaylistManager.getAliasModel().getAliases());

                    if(existingStream != null)
                    {
                        lines.add("Stream As TG " + streamAs + " is already used by '" + existingStream.getName() +
                            "' (" + existingStream.getAliasListName() + ").");
                    }
                }
            }
        }

        getAliasWarningLabel().setText(String.join("\n", lines));
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(getCreateButton().getScene().getWindow());
        alert.showAndWait();
    }

    /**
     * Shows a success message in the alias status area for a few seconds, after which any alias warning returns.
     */
    private void showStatus(String message)
    {
        if(mStatusTransition == null)
        {
            mStatusTransition = new PauseTransition(Duration.seconds(3));
            mStatusTransition.setOnFinished(event -> {
                mStatusShowing = false;
                getAliasWarningLabel().setStyle(WARNING_STYLE);
                updateAliasWarning();
            });
        }

        mStatusTransition.stop();
        mStatusShowing = true;
        getAliasWarningLabel().setStyle(SUCCESS_STYLE);
        getAliasWarningLabel().setText(message);
        mStatusTransition.playFromStart();
    }

    /**
     * Asks the user how to proceed when the talkgroup is already aliased in the alias list.
     * @param numberedListName next numbered alias list name (MasonKYSO -> MasonKYSO2)
     * @param channelListName alias list name from the channel name, or null
     */
    private ExistingAliasChoice confirmExisting(Protocol protocol, int talkgroup, Alias existing, String aliasListName,
                                                String numberedListName, String channelListName)
    {
        boolean offerChannelList = channelListName != null && !channelListName.equalsIgnoreCase(numberedListName);

        StringBuilder sb = new StringBuilder();
        sb.append("Talkgroup ").append(talkgroup).append(" is already aliased as '").append(existing.getName())
            .append("' in alias list '").append(aliasListName).append("'.\n\n");

        if(RadioReferenceAliasSupport.isDecodedTalkgroup(protocol))
        {
            String protocolLabel = protocol == Protocol.APCO25 ? "P25" : protocol.toString();
            sb.append(protocolLabel).append(" conventional channels nearly all use talkgroup ")
                .append(RadioReferenceAliasSupport.getConventionalDefaultTalkgroup(protocol))
                .append(". Aliases (and the streaming settings on them) can only tell these channels apart when ")
                .append("each channel has its own alias list.\n\n");
        }

        sb.append("'").append(numberedListName).append("': create the next numbered alias list for this channel ")
            .append("and its alias.\n");

        if(offerChannelList)
        {
            sb.append("'").append(channelListName).append("': create an alias list named from the channel name.\n");
        }

        sb.append("Duplicate Alias: add a second alias for the same talkgroup to '").append(aliasListName)
            .append("' (only one of them will match).\n")
            .append("Channel Only: create the channel in '").append(aliasListName).append("' without an alias.");

        ButtonType numberedList = new ButtonType("New List '" + numberedListName + "'", ButtonBar.ButtonData.YES);
        ButtonType channelList = new ButtonType("New List '" + channelListName + "'", ButtonBar.ButtonData.OTHER);
        ButtonType createAnyway = new ButtonType("Duplicate Alias", ButtonBar.ButtonData.OTHER);
        ButtonType channelOnly = new ButtonType("Channel Only", ButtonBar.ButtonData.NO);

        Alert alert = offerChannelList ?
            new Alert(Alert.AlertType.WARNING, sb.toString(), numberedList, channelList, createAnyway, channelOnly, ButtonType.CANCEL) :
            new Alert(Alert.AlertType.WARNING, sb.toString(), numberedList, createAnyway, channelOnly, ButtonType.CANCEL);
        alert.setTitle("Existing Alias");
        alert.setHeaderText("Talkgroup " + talkgroup + " Already Aliased");
        alert.getDialogPane().setMinWidth(680);
        alert.initOwner(getCreateButton().getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();

        if(result.isPresent())
        {
            if(result.get() == numberedList)
            {
                return ExistingAliasChoice.NEW_NUMBERED_LIST;
            }
            else if(offerChannelList && result.get() == channelList)
            {
                return ExistingAliasChoice.NEW_CHANNEL_LIST;
            }
            else if(result.get() == createAnyway)
            {
                return ExistingAliasChoice.CREATE_ANYWAY;
            }
            else if(result.get() == channelOnly)
            {
                return ExistingAliasChoice.CHANNEL_ONLY;
            }
        }

        return ExistingAliasChoice.CANCEL;
    }

    /**
     * Warns that a P25/NXDN conventional talkgroup isn't the conventional default and offers to use the default with
     * the entered value as the Stream As talkgroup.  Includes an option to turn the warning off.
     */
    private DefaultTalkgroupChoice confirmNonDefaultTalkgroup(Protocol protocol, int talkgroup, int conventionalDefault,
                                                              Integer streamAs)
    {
        String protocolLabel = protocol == Protocol.APCO25 ? "P25" : protocol.toString();
        boolean moveToStreamAs = streamAs == null && talkgroup >= RadioReferenceAliasSupport.MIN_TALKGROUP;

        String text = "Talkgroup " + talkgroup + " isn't the " + protocolLabel + " conventional default (" +
            conventionalDefault + ").\n\n" +
            "On a digital conventional channel the talkgroup is decoded off the air, and nearly every " + protocolLabel +
            " conventional system uses talkgroup " + conventionalDefault + ". An alias on talkgroup " + talkgroup +
            " only matches if the radios actually transmit talkgroup " + talkgroup + ". Otherwise the alias never " +
            "matches, and nothing set on it (streaming, recording, priority) applies.\n\n" +
            "To keep conventional channels apart when streaming, leave the talkgroup at " + conventionalDefault +
            " and use Stream As TG instead.";

        Label label = new Label(text);
        label.setWrapText(true);
        label.setPrefWidth(560);
        CheckBox dontWarn = new CheckBox("Don't warn me about conventional default talkgroups");
        VBox content = new VBox(15, label, dontWarn);

        ButtonType useDefault = new ButtonType(moveToStreamAs ? "Use TG " + conventionalDefault + ", Stream As " +
            talkgroup : "Use TG " + conventionalDefault, ButtonBar.ButtonData.YES);
        ButtonType keep = new ButtonType("Keep TG " + talkgroup, ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING, "", useDefault, keep, ButtonType.CANCEL);
        alert.setTitle("Conventional Talkgroup");
        alert.setHeaderText(protocolLabel + " Conventional Talkgroup " + talkgroup);
        alert.getDialogPane().setContent(content);
        alert.initOwner(getCreateButton().getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();

        DefaultTalkgroupChoice choice = DefaultTalkgroupChoice.CANCEL;

        if(result.isPresent())
        {
            if(result.get() == useDefault)
            {
                choice = DefaultTalkgroupChoice.USE_DEFAULT;
            }
            else if(result.get() == keep)
            {
                choice = DefaultTalkgroupChoice.KEEP;
            }
        }

        if(choice != DefaultTalkgroupChoice.CANCEL && dontWarn.isSelected())
        {
            mSettings.getRadioReferenceSettings().setConventionalDefaultTalkgroupWarning(false);
        }

        return choice;
    }

    /**
     * Follows changes to this setting made on the settings dialog's Radio Reference page
     */
    @Subscribe
    public void settingsUpdated(SettingsType settingsType)
    {
        if(settingsType == SettingsType.RADIO_REFERENCE)
        {
            Platform.runLater(() -> {
                getShowCreatedChannelCheckBox().setSelected(mSettings.getRadioReferenceSettings().getShowChannelEditor(mLevel));
                getOwnAliasListCheckBox().setSelected(mSettings.getRadioReferenceSettings().isConventionalOwnAliasList());
                getAutoStartCheckBox().setSelected(mSettings.getRadioReferenceSettings().isAutoStartImportedChannels());
                updateAliasWarning();
            });
        }
    }
}
