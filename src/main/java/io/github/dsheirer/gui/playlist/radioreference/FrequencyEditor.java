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
            getToneTextField().setText(item.getTone());
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
            mCreateButton.setOnAction(event -> {
                Channel channel = createChannel(mModeDecoderType, mFrequency, getSystemTextField().getText(),
                    getSiteTextField().getText(), getNameTextField().getText(), getToneTextField().getText());

                if(channel != null)
                {
                    Alias alias = null;
                    String aliasListName = getSelectedAliasListName();
                    Protocol protocol = RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType);
                    boolean channelAssigned = RadioReferenceAliasSupport.isChannelAssignedTalkgroup(protocol);
                    boolean createAlias = aliasListName != null && protocol != null && getCreateAliasCheckBox().isSelected();
                    Integer talkgroup = RadioReferenceAliasSupport.parseTalkgroup(getTalkgroupTextField().getText());

                    if(channelAssigned || createAlias)
                    {
                        if(talkgroup == null)
                        {
                            showError("Invalid Talkgroup", "Talkgroup must be a value between 1 and 65,535.");
                            return;
                        }

                        if(channelAssigned && channel.getDecodeConfiguration() instanceof DecodeConfigAnalog analog)
                        {
                            analog.setTalkgroup(talkgroup);
                        }
                    }

                    if(aliasListName != null)
                    {
                        channel.setAliasListName(aliasListName);
                    }

                    if(createAlias)
                    {
                        String streamAsText = getStreamAsTalkgroupTextField().getText();
                        Integer streamAs = RadioReferenceAliasSupport.parseTalkgroup(streamAsText);

                        if(streamAsText != null && !streamAsText.isBlank() && streamAs == null)
                        {
                            showError("Invalid Stream As Talkgroup", "Stream As Talkgroup must be blank or a value between 1 and 65,535.");
                            return;
                        }

                        String aliasName = getAliasNameTextField().getText();
                        if(aliasName == null || aliasName.isBlank())
                        {
                            aliasName = getNameTextField().getText();
                        }

                        boolean create = true;
                        Alias existing = RadioReferenceAliasSupport.findExisting(protocol, talkgroup,
                            getAliasListAliases(aliasListName));

                        if(existing != null)
                        {
                            Boolean choice = confirmExisting("Talkgroup " + talkgroup + " is already aliased as '" +
                                existing.getName() + "' in alias list '" + aliasListName + "'.");

                            if(choice == null)
                            {
                                return;
                            }

                            create = choice;
                        }

                        if(create)
                        {
                            alias = RadioReferenceAliasSupport.createAlias(aliasName, aliasListName,
                                getAliasGroupTextField().getText(), protocol, talkgroup, streamAs,
                                getSelectedBroadcastChannel());
                        }
                    }

                    getCreateButton().setDisable(true);
                    mPlaylistManager.getChannelModel().addChannel(channel);

                    if(alias != null)
                    {
                        mPlaylistManager.getAliasModel().addAlias(alias);
                    }

                    updateAliasControls(true);

                    if(getShowCreatedChannelCheckBox().selectedProperty().get())
                    {
                        MyEventBus.getGlobalEventBus().post(new ViewChannelRequest(channel));
                    }
                    else
                    {
                        getChannelCreatedLabel().setOpacity(1.0);
                        FadeTransition transition = new FadeTransition(Duration.seconds(2), getChannelCreatedLabel());
                        transition.setDelay(Duration.seconds(1));
                        transition.setToValue(0.0);
                        transition.play();
                    }
                }
            });
        }

        return mCreateButton;
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
            mTalkgroupTextField.setTooltip(new Tooltip("Analog: talkgroup assigned to the channel.  P25: decoded talkgroup to alias (default 1)."));
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
            mAliasWarningLabel.setStyle("-fx-text-fill: #d2691e;");
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
        String aliasListName = getSelectedAliasListName();
        boolean hasList = aliasListName != null;
        boolean supported = mModeDecoderType != null && mModeDecoderType.hasDecoderType() && mFrequency > 0;
        Protocol protocol = supported ? RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType) : null;
        boolean channelAssigned = RadioReferenceAliasSupport.isChannelAssignedTalkgroup(protocol);
        boolean createAlias = hasList && protocol != null && getCreateAliasCheckBox().isSelected();

        getRenameAliasListButton().setDisable(!hasList);
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
                int suggested = RadioReferenceAliasSupport.getSuggestedTalkgroup(protocol, getAliasListAliases(aliasListName));
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
     * Shows a warning when the talkgroup is already aliased in the selected alias list or the stream-as talkgroup is
     * already in use, and a note for channel types that are aliased by hand.
     */
    private void updateAliasWarning()
    {
        StringBuilder sb = new StringBuilder();
        String aliasListName = getSelectedAliasListName();
        boolean supported = mModeDecoderType != null && mModeDecoderType.hasDecoderType() && mFrequency > 0;
        Protocol protocol = supported ? RadioReferenceAliasSupport.getAliasProtocol(mModeDecoderType) : null;

        if(supported && aliasListName != null)
        {
            if(protocol == null)
            {
                sb.append("Alias list will be assigned to the channel. Create aliases for ")
                    .append(mModeDecoderType.getDecoderType().getDisplayString())
                    .append(" identifiers in the alias editor.");
            }
            else if(getCreateAliasCheckBox().isSelected())
            {
                Integer talkgroup = RadioReferenceAliasSupport.parseTalkgroup(getTalkgroupTextField().getText());

                if(talkgroup == null)
                {
                    sb.append("Talkgroup must be a value between 1 and 65,535.");
                }
                else
                {
                    Alias existing = RadioReferenceAliasSupport.findExisting(protocol, talkgroup,
                        getAliasListAliases(aliasListName));

                    if(existing != null)
                    {
                        sb.append("Talkgroup ").append(talkgroup).append(" is already aliased as '")
                            .append(existing.getName()).append("' in this alias list.");
                    }
                }

                Integer streamAs = RadioReferenceAliasSupport.parseTalkgroup(getStreamAsTalkgroupTextField().getText());

                if(streamAs != null)
                {
                    Alias existingStream = RadioReferenceAliasSupport.findStreamAsTalkgroup(streamAs,
                        mPlaylistManager.getAliasModel().getAliases());

                    if(existingStream != null)
                    {
                        if(!sb.isEmpty())
                        {
                            sb.append("\n");
                        }

                        sb.append("Stream As TG ").append(streamAs).append(" is already used by '")
                            .append(existingStream.getName()).append("' (").append(existingStream.getAliasListName())
                            .append(").");
                    }
                }
            }
        }

        getAliasWarningLabel().setText(sb.toString());
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
     * Asks the user how to proceed when the talkgroup is already aliased.
     * @return true to create the alias anyway, false to create the channel only, or null to cancel
     */
    private Boolean confirmExisting(String message)
    {
        ButtonType createAnyway = new ButtonType("Create Alias Anyway", ButtonBar.ButtonData.YES);
        ButtonType channelOnly = new ButtonType("Channel Only", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING, message + "\n\nCreate a duplicate alias, create the channel " +
            "without an alias, or cancel?", createAnyway, channelOnly, ButtonType.CANCEL);
        alert.setTitle("Existing Alias");
        alert.setHeaderText("Talkgroup Already Aliased");
        alert.initOwner(getCreateButton().getScene().getWindow());
        Optional<ButtonType> result = alert.showAndWait();

        if(result.isPresent())
        {
            if(result.get() == createAnyway)
            {
                return true;
            }
            else if(result.get() == channelOnly)
            {
                return false;
            }
        }

        return null;
    }

    /**
     * Follows changes to this setting made on the settings dialog's Radio Reference page
     */
    @Subscribe
    public void settingsUpdated(SettingsType settingsType)
    {
        if(settingsType == SettingsType.RADIO_REFERENCE)
        {
            Platform.runLater(() -> getShowCreatedChannelCheckBox().setSelected(mSettings.getRadioReferenceSettings().getShowChannelEditor(mLevel)));
        }
    }
}
