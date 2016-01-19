package org.tasgo.coherence.client.synchronizer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.client.FMLClientHandler;
import org.tasgo.coherence.Coherence;
import org.tasgo.coherence.client.ui.UiError;
import org.tasgo.coherence.client.ui.UiProgress;
import org.tasgo.coherence.client.ui.UiYesNo;
import org.tasgo.coherence.client.ui.UiYesNoCallback;

/**
 * Created by Tasgo on 1/18/16.
 */
public class Client implements UiYesNoCallback {
    private ServerData serverData;

    private enum Phase {INITIATION, SYNCHRONIZATION, COMPLETION}
    private GuiScreen parent;
    private UiProgress uiProgress;
    private Phase phase = Phase.INITIATION;

    private Initiator initiator;
    private Synchronizer synchronizer;
    private Completioner completioner;


    public Client(GuiScreen parent, ServerData serverData) {
        this.serverData = serverData;
        this.parent = parent;
        this.uiProgress = new UiProgress(parent, 100);
        if (Coherence.instance.postCohered) {
            FMLClientHandler.instance().connectToServer(parent, serverData);
            return;
        }

    }

    public void advance() {
        switch (phase) {
            case INITIATION:
                phase = Phase.SYNCHRONIZATION;
                StringBuilder sb = new StringBuilder(String.format("The server %s would like to load %d mods.",
                        initiator.address, initiator.neededmods.size()));
                sb.append("\nAre you okay with this?");
                FMLClientHandler.instance().showGuiScreen(new UiYesNo(this, sb.toString()));
                break;
            case SYNCHRONIZATION:
                phase = Phase.COMPLETION;
                break;
        }
    }

    @Override
    public void onClick(boolean result) {
        if (result) {
            switch (phase) {
                case SYNCHRONIZATION:
                    break;
                case COMPLETION:
                    break;
            }
        }

    }

    public UiProgress getProgressScreen() {
        return uiProgress;
    }

    public ServerData getServerData() {
        return serverData;
    }

    /*public Initiator getInitiator() {
        return initiator;
    }

    public Synchronizer getSynchronizer() {
        return synchronizer;
    }

    public Completioner getCompletioner() {
        return completioner
    }*/

    protected void crash(Exception e) {
        e.printStackTrace();
        UiError.crash(parent, e);
    }
}
