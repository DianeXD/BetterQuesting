package betterquesting.client.gui2;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.client.gui.misc.INeedsRefresh;
import betterquesting.api.enums.EnumLogic;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineEntry;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.GuiScreenCanvas;
import betterquesting.api2.client.gui.controls.IPanelButton;
import betterquesting.api2.client.gui.controls.PanelButton;
import betterquesting.api2.client.gui.controls.PanelButtonQuest;
import betterquesting.api2.client.gui.controls.PanelButtonStorage;
import betterquesting.api2.client.gui.events.IPEventListener;
import betterquesting.api2.client.gui.events.PEventBroadcaster;
import betterquesting.api2.client.gui.events.PanelEvent;
import betterquesting.api2.client.gui.events.types.PEventButton;
import betterquesting.api2.client.gui.misc.GuiAlign;
import betterquesting.api2.client.gui.misc.GuiPadding;
import betterquesting.api2.client.gui.misc.GuiRectangle;
import betterquesting.api2.client.gui.misc.GuiTransform;
import betterquesting.api2.client.gui.panels.CanvasTextured;
import betterquesting.api2.client.gui.panels.bars.PanelVScrollBar;
import betterquesting.api2.client.gui.panels.content.PanelGeneric;
import betterquesting.api2.client.gui.panels.content.PanelTextBox;
import betterquesting.api2.client.gui.panels.lists.CanvasHoverTray;
import betterquesting.api2.client.gui.panels.lists.CanvasQuestLine;
import betterquesting.api2.client.gui.panels.lists.CanvasScrolling;
import betterquesting.api2.client.gui.popups.PopChoice;
import betterquesting.api2.client.gui.resources.colors.GuiColorPulse;
import betterquesting.api2.client.gui.resources.colors.GuiColorStatic;
import betterquesting.api2.client.gui.resources.textures.GuiTextureColored;
import betterquesting.api2.client.gui.resources.textures.OreDictTexture;
import betterquesting.api2.client.gui.themes.presets.PresetColor;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.client.gui.themes.presets.PresetTexture;
import betterquesting.api2.storage.DBEntry;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.api2.utils.Tuple2;
import betterquesting.client.gui2.editors.GuiQuestLinesEditor;
import betterquesting.client.gui2.editors.designer.GuiDesigner;
import betterquesting.handlers.ConfigHandler;
import betterquesting.network.handlers.NetQuestAction;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.Configuration;
import org.lwjgl.util.vector.Vector4f;

import java.util.*;

import static betterquesting.api.storage.BQ_Settings.alwaysDrawImplicit;

public class GuiQuestLines extends GuiScreenCanvas implements IPEventListener, INeedsRefresh
{
    private ScrollPosition scrollPosition;

    public static class ScrollPosition{
        public ScrollPosition(int chapterScrollY) {
            this.chapterScrollY = chapterScrollY;
        }

        private int chapterScrollY;

        public int getChapterScrollY() {
            return chapterScrollY;
        }

        public void setChapterScrollY(int chapterScrollY) {
            this.chapterScrollY = chapterScrollY;
        }
    }

    private IQuestLine selectedLine = null;
    private static int selectedLineId = -1;
    
    private final List<Tuple2<DBEntry<IQuestLine>, Integer>> visChapters = new ArrayList<>();
    
    private CanvasQuestLine cvQuest;
    
    // Keep these separate for now
    private static CanvasHoverTray cvChapterTray;
    private static CanvasHoverTray cvDescTray;
    private static CanvasHoverTray cvFrame;
    
    private CanvasScrolling cvDesc;
    private PanelVScrollBar scDesc;
    private CanvasScrolling cvLines;
    private PanelVScrollBar scLines;
    
    private PanelGeneric icoChapter;
    private PanelTextBox txTitle;
    private PanelTextBox txDesc;
    
    private PanelButton claimAll;
    
    private static boolean trayLock;
    private static boolean viewMode;
    
    private final List<PanelButtonStorage<DBEntry<IQuestLine>>> btnListRef = new ArrayList<>();

    public GuiQuestLines(GuiScreen parent)
    {
        super(parent);
        trayLock = BQ_Settings.lockTray;
        viewMode = BQ_Settings.viewMode;

        if (scrollPosition == null) {
            scrollPosition = new ScrollPosition(0);
        }
    }
    
    @Override
    public void refreshGui()
    {
        refreshChapterVisibility();
        refreshContent();
    }
    
    @Override
    public void initPanel()
    {
        super.initPanel();

        GuiHome.bookmark = this;
        // If we come to quests gui - we set skip home to true
        if (!BQ_Settings.skipHome){
            ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Skip home", false).set(true);
            ConfigHandler.config.save();
            BQ_Settings.skipHome = true;
        }
        
        if(selectedLineId >= 0)
        {
            selectedLine = QuestLineDatabase.INSTANCE.getValue(selectedLineId);
            if(selectedLine == null) selectedLineId = -1;
        } else
        {
            selectedLine = null;
        }
        
        boolean canEdit = QuestingAPI.getAPI(ApiReference.SETTINGS).canUserEdit(mc.thePlayer);
        boolean preOpen = false;
        // First time load, if tray locked - let the tray open
        if(trayLock && cvChapterTray == null && cvDescTray == null) preOpen = true;
        if(trayLock && cvChapterTray != null && cvChapterTray.isTrayOpen()) preOpen = true;
        if(trayLock && cvDescTray != null && cvDescTray.isTrayOpen()) preOpen = true;
        
        PEventBroadcaster.INSTANCE.register(this, PEventButton.class);
        
        CanvasTextured cvBackground = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), PresetTexture.PANEL_MAIN.getTexture());
        this.addPanel(cvBackground);
        
        PanelButton btnExit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -24, 32, 16, 0), -1, "").setIcon(PresetIcon.ICON_PG_PREV.getTexture());
        btnExit.setClickAction((b) -> mc.displayGuiScreen(parent));
        btnExit.setTooltip(Collections.singletonList(QuestTranslation.translate("gui.back")));
        cvBackground.addPanel(btnExit);

        // Search button
        PanelButton btnSearch = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -40, 32, 16, 0), -1, "").setIcon(PresetIcon.ICON_ZOOM.getTexture());
        btnSearch.setClickAction(this::openSearch);
        btnSearch.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.gui.search")));
        cvBackground.addPanel(btnSearch);
        
        if(canEdit)
        {
            PanelButton btnEdit = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 8, -56, 16, 16, 0), -1, "").setIcon(PresetIcon.ICON_GEAR.getTexture());
            btnEdit.setClickAction((b) -> mc.displayGuiScreen(new GuiQuestLinesEditor(this)));
            btnEdit.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.edit")));
            cvBackground.addPanel(btnEdit);
            
            PanelButton btnDesign = new PanelButton(new GuiTransform(GuiAlign.BOTTOM_LEFT, 24, -56, 16, 16, 0), -1, "").setIcon(PresetIcon.ICON_SORT.getTexture());
            btnDesign.setClickAction((b) -> {
                if(selectedLine != null) mc.displayGuiScreen(new GuiDesigner(this, selectedLine));
            });
            btnDesign.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.designer")));
            cvBackground.addPanel(btnDesign);
        }
        
        txTitle = new PanelTextBox(new GuiTransform(new Vector4f(0F, 0F, 0.5F, 0F), new GuiPadding(60, 12, 0, -24), 0), "");
        txTitle.setColor(PresetColor.TEXT_HEADER.getColor());
        cvBackground.addPanel(txTitle);
        
        icoChapter = new PanelGeneric(new GuiTransform(GuiAlign.TOP_LEFT, 40, 8, 16, 16, 0), null);
        cvBackground.addPanel(icoChapter);
    
        cvFrame = new CanvasHoverTray(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40 + 150 + 24, 24, 8, 8), 0), new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40, 24, 8, 8), 0), PresetTexture.AUX_FRAME_0.getTexture());
        cvFrame.setManualOpen(true);
        //CanvasTextured cvFrame = new CanvasTextured(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(40, 24, 8, 8), 0), PresetTexture.AUX_FRAME_0.getTexture());
        cvBackground.addPanel(cvFrame);
        cvFrame.setTrayState(!preOpen, 1);
        // These would probably be more annoying than useful if you just wanted to check a tray but not lose your position
        //cvFrame.setOpenAction(() -> cvQuest.fitToWindow());
        //cvFrame.setCloseAction(() -> cvQuest.fitToWindow());

        // === TRAY STATE ===

        boolean chapterTrayOpened = trayLock && cvChapterTray != null && cvChapterTray.isTrayOpen();
        boolean descTrayOpened = trayLock && cvDescTray != null && cvDescTray.isTrayOpen();
        if (preOpen && !chapterTrayOpened && !descTrayOpened)
            chapterTrayOpened = true;

        // === CHAPTER TRAY ===

        cvChapterTray = new CanvasHoverTray(new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -24, 8), -1), new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -40 - 150 - 24, 8), -1), PresetTexture.PANEL_INNER.getTexture());
        cvChapterTray.setManualOpen(true);
        cvChapterTray.setOpenAction(() -> {
            cvDescTray.setTrayState(false, 200);
            cvFrame.setTrayState(false, 200);
            buildChapterList();
        });
        cvBackground.addPanel(cvChapterTray);

        cvLines = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 16, 8), 0));
        cvChapterTray.getCanvasOpen().addPanel(cvLines);

        scLines = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-16, 8, 8, 8), 0));
        cvLines.setScrollDriverY(scLines);
        cvChapterTray.getCanvasOpen().addPanel(scLines);

        // === DESCRIPTION TRAY ===

        cvDescTray = new CanvasHoverTray(new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -24, 8), -1), new GuiTransform(GuiAlign.LEFT_EDGE, new GuiPadding(40, 24, -40 - 150 - 24, 8), -1), PresetTexture.PANEL_INNER.getTexture());
        cvDescTray.setManualOpen(true);
        cvDescTray.setOpenAction(() -> {
            cvChapterTray.setTrayState(false, 200);
            cvFrame.setTrayState(false, 200);
            cvDesc.resetCanvas();
            if(selectedLine != null)
            {
                txDesc = new PanelTextBox(new GuiRectangle(0, 0, cvDesc.getTransform().getWidth(), 0, 0), QuestTranslation.translate(selectedLine.getUnlocalisedDescription()), true);
                txDesc.setColor(PresetColor.TEXT_AUX_0.getColor());//.setFontSize(10);
                cvDesc.addCulledPanel(txDesc, false);
                cvDesc.refreshScrollBounds();
                scDesc.setEnabled(cvDesc.getScrollBounds().getHeight() > 0);
            } else
            {
                scDesc.setEnabled(false);
            }
        });
        cvBackground.addPanel(cvDescTray);
        
        cvDesc = new CanvasScrolling(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(8, 8, 20, 8), 0));
        cvDescTray.getCanvasOpen().addPanel(cvDesc);
    
        scDesc = new PanelVScrollBar(new GuiTransform(GuiAlign.RIGHT_EDGE, new GuiPadding(-16, 8, 8, 8), 0));
        cvDesc.setScrollDriverY(scDesc);
        cvDescTray.getCanvasOpen().addPanel(scDesc);
        
        // === LEFT SIDEBAR ===
        int yOff = 24;
        PanelButton btnTrayToggle = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, 0), -1, "");
        btnTrayToggle.setIcon(PresetIcon.ICON_BOOKMARK.getTexture(), selectedLineId < 0 && !chapterTrayOpened ? new GuiColorPulse(0xFFFFFFFF, 0xFF444444, 2F, 0F) : new GuiColorStatic(0xFFFFFFFF), 0);
        btnTrayToggle.setClickAction((b) -> {
            cvFrame.setTrayState(cvChapterTray.isTrayOpen(), 200);
            cvChapterTray.setTrayState(!cvChapterTray.isTrayOpen(), 200);
            btnTrayToggle.setIcon(PresetIcon.ICON_BOOKMARK.getTexture());
        });
        btnTrayToggle.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.title.quest_lines")));
        cvBackground.addPanel(btnTrayToggle);
        yOff += 16;
        
        PanelButton btnDescToggle = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, 0), -1, "").setIcon(PresetIcon.ICON_DESC.getTexture());
        btnDescToggle.setClickAction((b) -> {
            cvFrame.setTrayState(cvDescTray.isTrayOpen(), 200);
            cvDescTray.setTrayState(!cvDescTray.isTrayOpen(), 200);
        });
        btnDescToggle.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.gui.description")));
        cvBackground.addPanel(btnDescToggle);
        yOff += 16;

        claimAll = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "");
        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture());
        claimAll.setClickAction((b) -> {
            if (BQ_Settings.claimAllConfirmation) {
                openPopup(new PopChoice(QuestTranslation.translate("betterquesting.gui.claim_all_warning") + "\n\n" + QuestTranslation.translate("betterquesting.gui.claim_all_confirm"), PresetIcon.ICON_CHEST_ALL.getTexture(), integer -> {
                    if (integer == 1){
                        ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Claim all requires confirmation", true).set(false);
                        ConfigHandler.config.save();
                        ConfigHandler.initConfigs();
                    }
                    if(integer <= 1){
                        claimAll();
                    }
                }, QuestTranslation.translate("gui.yes"), QuestTranslation.translate("betterquesting.gui.yes_always"), QuestTranslation.translate("gui.no")));
            }else{
                claimAll();
            }
        });
        claimAll.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.claim_all")));
        cvBackground.addPanel(claimAll);
        yOff += 16;
        
        PanelButton fitView = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), 5, "");
        fitView.setIcon(PresetIcon.ICON_BOX_FIT.getTexture());
        fitView.setClickAction((b) -> {
            if(cvQuest.getQuestLine() != null) cvQuest.fitToWindow();
        });
        fitView.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.zoom_fit")));
        cvBackground.addPanel(fitView);
        yOff += 16;
        
        // The Jester1147 button
        PanelButton btnTrayLock = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "").setIcon(trayLock ? PresetIcon.ICON_LOCKED.getTexture() : PresetIcon.ICON_UNLOCKED.getTexture());
        btnTrayLock.setClickAction((b) -> {
            trayLock = !trayLock;
            b.setIcon(trayLock ? PresetIcon.ICON_LOCKED.getTexture() : PresetIcon.ICON_UNLOCKED.getTexture());
            ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Lock tray", false).set(trayLock);
            ConfigHandler.config.save();
            ConfigHandler.initConfigs();
        });
        btnTrayLock.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.lock_tray")));
        cvBackground.addPanel(btnTrayLock);
        yOff += 16;

        // View Mode Button
        if (BQ_Settings.viewModeBtn) {
            PanelButton btnViewMode = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "").setIcon(viewMode ? PresetIcon.ICON_VIEW_MODE_ON.getTexture() : PresetIcon.ICON_VIEW_MODE_OFF.getTexture());
            btnViewMode.setClickAction((b) -> {
                viewMode = !viewMode;
                b.setIcon(viewMode ? PresetIcon.ICON_VIEW_MODE_ON.getTexture() : PresetIcon.ICON_VIEW_MODE_OFF.getTexture());
                ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "View mode", false).set(viewMode);
                ConfigHandler.config.save();
                ConfigHandler.initConfigs();
                refreshGui();
            });
            btnViewMode.setTooltip(Collections.singletonList(QuestTranslation.translate("betterquesting.btn.view_mode")));
            cvBackground.addPanel(btnViewMode);
            yOff += 16;
        }

        PanelButton btnViewMode = new PanelButton(new GuiTransform(GuiAlign.TOP_LEFT, 8, yOff, 32, 16, -2), -1, "").setIcon(alwaysDrawImplicit ? PresetIcon.ICON_VISIBILITY_IMPLICIT.getTexture() : PresetIcon.ICON_VISIBILITY_NORMAL.getTexture());
        btnViewMode.setClickAction((b) -> {
            alwaysDrawImplicit = !alwaysDrawImplicit;
            b.setIcon(alwaysDrawImplicit ? PresetIcon.ICON_VISIBILITY_IMPLICIT.getTexture() : PresetIcon.ICON_VISIBILITY_NORMAL.getTexture());
            ConfigHandler.config.get(Configuration.CATEGORY_GENERAL, "Always draw implicit dependency", false, "If true, always draw implicit dependency. This property can be changed by the GUI").set(alwaysDrawImplicit);
            ConfigHandler.config.save();
            btnViewMode.setTooltip(Arrays.asList(QuestTranslation.translate("betterquesting.btn.always_draw_implicit"), QuestTranslation.translate("betterquesting.tooltip.cycle." + alwaysDrawImplicit)));
            ConfigHandler.initConfigs();
            refreshGui();
        });
        btnViewMode.setTooltip(Arrays.asList(QuestTranslation.translate("betterquesting.btn.always_draw_implicit"), QuestTranslation.translate("betterquesting.tooltip.cycle." + alwaysDrawImplicit)));
        cvBackground.addPanel(btnViewMode);
        
        // === CHAPTER VIEWPORT ===
        
        CanvasQuestLine oldCvQuest = cvQuest;
        cvQuest = new CanvasQuestLine(new GuiTransform(GuiAlign.FULL_BOX, new GuiPadding(0, 0, 0, 0), 0), 2);
        cvFrame.addPanel(cvQuest);
    
        if(selectedLine != null)
        {
            cvQuest.setQuestLine(selectedLine);
            
            if(oldCvQuest != null)
            {
                cvQuest.setZoom(oldCvQuest.getZoom());
                cvQuest.setScrollX(oldCvQuest.getScrollX());
                cvQuest.setScrollY(oldCvQuest.getScrollY());
                cvQuest.refreshScrollBounds();
                cvQuest.updatePanelScroll();
            }
            
            txTitle.setText(QuestTranslation.translate(selectedLine.getUnlocalisedName()));
            icoChapter.setTexture(new OreDictTexture(1F, selectedLine.getProperty(NativeProps.ICON), false, true), null);
        }
        
        // === MISC ===
        
        cvChapterTray.setTrayState(chapterTrayOpened, 1);
        cvDescTray.setTrayState(descTrayOpened, 1);
        
        refreshChapterVisibility();
        refreshClaimAll();

        cvLines.setScrollY(scrollPosition.getChapterScrollY());
        cvLines.updatePanelScroll();
    }

    @Override
    public boolean onMouseRelease(int mx, int my, int click) {
        try {
            return super.onMouseRelease(mx, my, click);
        } finally {
            if (cvLines != null) {
                scrollPosition.setChapterScrollY(cvLines.getScrollY());
            }
        }
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        try {
            return super.onMouseScroll(mx, my, scroll);
        } finally {
            if (cvLines != null) {
                scrollPosition.setChapterScrollY(cvLines.getScrollY());
            }
        }
    }

    private void claimAll() {
        if(cvQuest.getQuestButtons().size() <= 0) return;
        List<Integer> claimIdList = new ArrayList<>();
        for(PanelButtonQuest pbQuest : cvQuest.getQuestButtons())
        {
            IQuest q = pbQuest.getStoredValue().getValue();
            if(q.getRewards().size() > 0 && q.canClaim(mc.thePlayer)) claimIdList.add(pbQuest.getStoredValue().getID());
        }

        int[] cIDs = new int[claimIdList.size()];
        for(int i = 0; i < cIDs.length; i++)
        {
            cIDs[i] = claimIdList.get(i);
        }

        NetQuestAction.requestClaim(cIDs);
        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
    }

    @Override
    public void onPanelEvent(PanelEvent event)
    {
        if(event instanceof PEventButton)
        {
            onButtonPress((PEventButton)event);
        }
    }
    
    // TODO: Change CanvasQuestLine to NOT need these panel events anymore
    private void onButtonPress(PEventButton event)
    {
        Minecraft mc = Minecraft.getMinecraft();
        IPanelButton btn = event.getButton();
        
        if(btn.getButtonID() == 2 && btn instanceof PanelButtonStorage) // Quest Instance Select
        {
            @SuppressWarnings("unchecked")
            DBEntry<IQuest> quest = ((PanelButtonStorage<DBEntry<IQuest>>)btn).getStoredValue();
            GuiHome.bookmark = new GuiQuest(this, quest.getID());
            
            mc.displayGuiScreen(GuiHome.bookmark);
        }
    }
    
    private void refreshChapterVisibility()
    {
        boolean canEdit = QuestingAPI.getAPI(ApiReference.SETTINGS).canUserEdit(mc.thePlayer);
        List<DBEntry<IQuestLine>> lineList = QuestLineDatabase.INSTANCE.getSortedEntries();
        this.visChapters.clear();
        UUID playerID = QuestingAPI.getQuestingUUID(mc.thePlayer);
        
        for(DBEntry<IQuestLine> dbEntry : lineList)
        {
            IQuestLine ql = dbEntry.getValue();
            EnumQuestVisibility vis = ql.getProperty(NativeProps.VISIBILITY);
            if(!canEdit && vis == EnumQuestVisibility.HIDDEN) continue;
        
            boolean show = false;
            boolean unlocked = false;
            boolean complete = false;
            boolean allComplete = true;
            boolean pendingClaim = false;
        
            if(canEdit)
            {
                show = true;
                unlocked = true;
                complete = true;
            }

            if(BQ_Settings.viewMode)
            {
                show = true;
            }

            for(DBEntry<IQuestLineEntry> qID : ql.getEntries())
            {
                IQuest q = QuestDatabase.INSTANCE.getValue(qID.getID());
                if(q == null) continue;

                if(allComplete && !isQuestCompletedForQuestline(playerID, q)) allComplete = false;
                if(!pendingClaim && q.canClaimBasically(mc.thePlayer)) pendingClaim = true;
                if(!unlocked && q.isUnlocked(playerID)) unlocked = true;
                if(!complete && q.isComplete(playerID)) complete = true;
                if(!show && QuestCache.isQuestShown(q, playerID, mc.thePlayer)) show = true;
                if(unlocked && complete && show && pendingClaim && !allComplete) break;
            }
        
            if(vis == EnumQuestVisibility.COMPLETED && !complete)
            {
                continue;
            } else if(vis == EnumQuestVisibility.UNLOCKED && !unlocked)
            {
                continue;
            }
            
            int val = pendingClaim ? 1 : 0;
            if(allComplete) val |= 2;
            if(!show) val |= 4;
            
            visChapters.add(new Tuple2<>(dbEntry, val));
        }
        
        if(cvChapterTray.isTrayOpen()) buildChapterList();
    }

    private boolean isQuestCompletedForQuestline(UUID playerID, IQuest q) {
        if (q.isComplete(playerID)) return true; // Completed quest
        if (q.getProperty(NativeProps.VISIBILITY) == EnumQuestVisibility.HIDDEN) return true; // Always hidden quest
        if (q.getProperty(NativeProps.LOGIC_QUEST) == EnumLogic.XOR){ // Quest with choice
            int reqCount = 0;
            for (int qRequirementId : q.getRequirements()) {
                IQuest quest = QuestDatabase.INSTANCE.getValue(qRequirementId);
                if (quest.isComplete(playerID)) reqCount++;
                if (reqCount == 2) return true;
            }
        }

        return false;
    }

    private void buildChapterList()
    {
        cvLines.resetCanvas();
        btnListRef.clear();
        
        int listW = cvLines.getTransform().getWidth();
        
        for(int n = 0; n < visChapters.size(); n++)
        {
            DBEntry<IQuestLine> entry = visChapters.get(n).getFirst();
            int vis = visChapters.get(n).getSecond();
            
            cvLines.addPanel(new PanelGeneric(new GuiRectangle(0, n * 16, 16, 16, 0), new OreDictTexture(1F, entry.getValue().getProperty(NativeProps.ICON), false, true)));
            
            if((vis & 1) > 0)
            {
                cvLines.addPanel(new PanelGeneric(new GuiRectangle(8, n * 16 + 8, 8, 8, -1), new GuiTextureColored(PresetIcon.ICON_NOTICE.getTexture(), new GuiColorStatic(0xFFFFFF00))));
            } else if((vis & 2) > 0)
            {
                cvLines.addPanel(new PanelGeneric(new GuiRectangle(8, n * 16 + 8, 8, 8, -1), new GuiTextureColored(PresetIcon.ICON_TICK.getTexture(), new GuiColorStatic(0xFF00FF00))));
            }
            PanelButtonStorage<DBEntry<IQuestLine>> btnLine = new PanelButtonStorage<>(new GuiRectangle(16, n * 16, listW - 16, 16, 0), 1, QuestTranslation.translate(entry.getValue().getUnlocalisedName()), entry);
            btnLine.setTextAlignment(0);
            btnLine.setActive((vis & 4) == 0 && entry.getID() != selectedLineId);
            btnLine.setCallback((q) -> {
                openQuestLine(q);
            });
            cvLines.addPanel(btnLine);
            btnListRef.add(btnLine);
        }
        
        cvLines.refreshScrollBounds();
        scLines.setEnabled(cvLines.getScrollBounds().getHeight() > 0);
    }

    private void openQuestLine(DBEntry<IQuestLine> q) {
        selectedLine = q.getValue();
        selectedLineId = q.getID();
        for (int i = 0; i < btnListRef.size(); i++) {
            btnListRef.get(i).setActive((visChapters.get(i).getSecond() & 4) == 0 && btnListRef.get(i).getStoredValue().getID() != selectedLineId);
        }

        cvQuest.setQuestLine(q.getValue());
        icoChapter.setTexture(new OreDictTexture(1F, q.getValue().getProperty(NativeProps.ICON), false, true), null);
        txTitle.setText(QuestTranslation.translate(q.getValue().getUnlocalisedName()));
        if(!trayLock)
        {
            cvFrame.setTrayState(true, 200);
            cvChapterTray.setTrayState(false, 200);
            cvQuest.fitToWindow();
        }
        refreshClaimAll();
    }

    private void refreshContent()
    {
        if(selectedLineId >= 0)
        {
            selectedLine = QuestLineDatabase.INSTANCE.getValue(selectedLineId);
            if(selectedLine == null) selectedLineId = -1;
        } else
        {
            selectedLine = null;
        }
        
        float zoom = cvQuest.getZoom();
        int sx = cvQuest.getScrollX();
        int sy = cvQuest.getScrollY();
        /*if(cvQuest.getQuestLine() != selectedLine)*/ cvQuest.setQuestLine(selectedLine);
        cvQuest.setZoom(zoom);
        cvQuest.setScrollX(sx);
        cvQuest.setScrollY(sy);
        cvQuest.refreshScrollBounds();
        cvQuest.updatePanelScroll();
        
        if(selectedLine != null)
        {
            txTitle.setText(QuestTranslation.translate(selectedLine.getUnlocalisedName()));
            icoChapter.setTexture(new OreDictTexture(1F, selectedLine.getProperty(NativeProps.ICON), false, true), null);
        } else
        {
            txTitle.setText("");
            icoChapter.setTexture(null, null);
        }
        
        refreshClaimAll();
    }
    
    private void refreshClaimAll()
    {
        if(cvQuest.getQuestLine() == null || cvQuest.getQuestButtons().size() <= 0)
        {
            claimAll.setActive(false);
            claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
            return;
        }
        
        for(PanelButtonQuest btn : cvQuest.getQuestButtons())
        {
            if(btn.getStoredValue().getValue().canClaim(mc.thePlayer))
            {
                claimAll.setActive(true);
                claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorPulse(0xFFFFFFFF, 0xFF444444, 2F, 0F), 0);
                return;
            }
        }
        
        claimAll.setIcon(PresetIcon.ICON_CHEST_ALL.getTexture(), new GuiColorStatic(0xFF444444), 0);
        claimAll.setActive(false);
    }

    private void openSearch(PanelButton panelButton) {
        GuiQuestSearch guiQuestSearch = new GuiQuestSearch(this);
        guiQuestSearch.setCallback(entry -> {
            openQuestLine(entry.getQuestLineEntry());
            int selectedQuestId = entry.getQuest().getID();
            Optional<PanelButtonQuest> targetQuestButton = cvQuest.getQuestButtons().stream().filter(panelButtonQuest -> panelButtonQuest.getStoredValue().getID() == selectedQuestId).findFirst();
            targetQuestButton.ifPresent(panelButtonQuest -> {
                GuiTextureColored newTexture = new GuiTextureColored(panelButtonQuest.txFrame,
                        new GuiColorPulse(
                                new GuiColorStatic(255, 220, 115, 255),
                                new GuiColorStatic(255, 191, 0, 255),
                                1, 0
                        ));
                panelButtonQuest.setTextures(newTexture, newTexture, newTexture);
            });
        });
        mc.displayGuiScreen(guiQuestSearch);
    }
}
