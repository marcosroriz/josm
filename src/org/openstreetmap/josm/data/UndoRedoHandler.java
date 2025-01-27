// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data;

import java.util.Collections;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;

/**
 * This is the global undo/redo handler for all {@link DataSet}s.
 * <p>
 * If you want to change a data set, you can use {@link #add(Command)} to execute a command on it and make that command undoable.
 */
public final class UndoRedoHandler {

    /**
     * All commands that were made on the dataset
     *
     * @see #getLastCommand()
     * @see #getUndoCommands()
     */
    private final LinkedList<Command> commands = new LinkedList<>();

    /**
     * The stack for redoing commands

     * @see #getRedoCommands()
     */
    private final LinkedList<Command> redoCommands = new LinkedList<>();

    private final LinkedList<CommandQueueListener> listenerCommands = new LinkedList<>();
    private final LinkedList<CommandQueuePreciseListener> preciseListenerCommands = new LinkedList<>();

    private static class InstanceHolder {
        static final UndoRedoHandler INSTANCE = new UndoRedoHandler();
    }

    /**
     * Returns the unique instance.
     * @return the unique instance
     * @since 14134
     */
    public static UndoRedoHandler getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Constructs a new {@code UndoRedoHandler}.
     */
    private UndoRedoHandler() {
        // Hide constructor
    }

    /**
     * A simple listener that gets notified of command queue (undo/redo) size changes.
     * @see CommandQueuePreciseListener
     * @since 12718 (moved from {@code OsmDataLayer}
     */
    @FunctionalInterface
    public interface CommandQueueListener {
        /**
         * Notifies the listener about the new queue size
         * @param queueSize Undo stack size
         * @param redoSize Redo stack size
         */
        void commandChanged(int queueSize, int redoSize);
    }

    /**
     * A listener that gets notified of command queue (undo/redo) operations individually.
     * @see CommandQueueListener
     * @since 13729
     */
    public interface CommandQueuePreciseListener {

        /**
         * Notifies the listener about a new command added to the queue.
         * @param e event
         */
        void commandAdded(CommandAddedEvent e);

        /**
         * Notifies the listener about commands being cleaned.
         * @param e event
         */
        void cleaned(CommandQueueCleanedEvent e);

        /**
         * Notifies the listener about a command that has been undone.
         * @param e event
         */
        void commandUndone(CommandUndoneEvent e);

        /**
         * Notifies the listener about a command that has been redone.
         * @param e event
         */
        void commandRedone(CommandRedoneEvent e);
    }

    abstract static class CommandQueueEvent extends EventObject {
        protected CommandQueueEvent(UndoRedoHandler source) {
            super(Objects.requireNonNull(source));
        }

        /**
         * Calls the appropriate method of the listener for this event.
         * @param listener dataset listener to notify about this event
         */
        abstract void fire(CommandQueuePreciseListener listener);

        @Override
        public final UndoRedoHandler getSource() {
            return (UndoRedoHandler) super.getSource();
        }
    }

    /**
     * Event fired after a command has been added to the command queue.
     * @since 13729
     */
    public static final class CommandAddedEvent extends CommandQueueEvent {

        private static final long serialVersionUID = 1L;
        private final Command cmd;

        private CommandAddedEvent(UndoRedoHandler source, Command cmd) {
            super(source);
            this.cmd = Objects.requireNonNull(cmd);
        }

        /**
         * Returns the added command.
         * @return the added command
         */
        public Command getCommand() {
            return cmd;
        }

        @Override
        void fire(CommandQueuePreciseListener listener) {
            listener.commandAdded(this);
        }
    }

    /**
     * Event fired after the command queue has been cleaned.
     * @since 13729
     */
    public static final class CommandQueueCleanedEvent extends CommandQueueEvent {

        private static final long serialVersionUID = 1L;
        private final DataSet ds;

        private CommandQueueCleanedEvent(UndoRedoHandler source, DataSet ds) {
            super(source);
            this.ds = ds;
        }

        /**
         * Returns the affected dataset.
         * @return the affected dataset, or null if the queue has been globally emptied
         */
        public DataSet getDataSet() {
            return ds;
        }

        @Override
        void fire(CommandQueuePreciseListener listener) {
            listener.cleaned(this);
        }
    }

    /**
     * Event fired after a command has been undone.
     * @since 13729
     */
    public static final class CommandUndoneEvent extends CommandQueueEvent {

        private static final long serialVersionUID = 1L;
        private final Command cmd;

        private CommandUndoneEvent(UndoRedoHandler source, Command cmd) {
            super(source);
            this.cmd = Objects.requireNonNull(cmd);
        }

        /**
         * Returns the undone command.
         * @return the undone command
         */
        public Command getCommand() {
            return cmd;
        }

        @Override
        void fire(CommandQueuePreciseListener listener) {
            listener.commandUndone(this);
        }
    }

    /**
     * Event fired after a command has been redone.
     * @since 13729
     */
    public static final class CommandRedoneEvent extends CommandQueueEvent {

        private static final long serialVersionUID = 1L;
        private final Command cmd;

        private CommandRedoneEvent(UndoRedoHandler source, Command cmd) {
            super(source);
            this.cmd = Objects.requireNonNull(cmd);
        }

        /**
         * Returns the redone command.
         * @return the redone command
         */
        public Command getCommand() {
            return cmd;
        }

        @Override
        void fire(CommandQueuePreciseListener listener) {
            listener.commandRedone(this);
        }
    }

    /**
     * Returns all commands that were made on the dataset, that can be undone.
     * @return all commands that were made on the dataset, that can be undone
     * @since 14281, 16567 (signature)
     */
    public List<Command> getUndoCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * Returns all commands that were made and undone on the dataset, that can be redone.
     * @return all commands that were made and undone on the dataset, that can be redone.
     * @since 14281, 16567 (signature)
     */
    public List<Command> getRedoCommands() {
        return Collections.unmodifiableList(redoCommands);
    }

    /**
     * Gets the last command that was executed on the command stack.
     * @return That command or <code>null</code> if there is no such command.
     * @since #12316
     */
    public Command getLastCommand() {
        return commands.peekLast();
    }

    /**
     * Determines if commands can be undone.
     * @return {@code true} if at least a command can be undone
     * @since 14281
     */
    public boolean hasUndoCommands() {
        return !commands.isEmpty();
    }

    /**
     * Determines if commands can be redone.
     * @return {@code true} if at least a command can be redone
     * @since 14281
     */
    public boolean hasRedoCommands() {
        return !redoCommands.isEmpty();
    }

    /**
     * Executes the command and add it to the intern command queue.
     * @param c The command to execute. Must not be {@code null}.
     */
    public void addNoRedraw(final Command c) {
        addNoRedraw(c, true);
    }

    /**
     * Executes the command and add it to the intern command queue.
     * @param c The command to execute. Must not be {@code null}.
     * @param execute true: Execute, else it is assumed that the command was already executed
     * @since 14845
     */
    public void addNoRedraw(final Command c, boolean execute) {
        CheckParameterUtil.ensureParameterNotNull(c, "c");
        if (execute) {
            c.executeCommand();
        }
        commands.add(c);
        // Limit the number of commands in the undo list.
        // Currently you have to undo the commands one by one. If
        // this changes, a higher default value may be reasonable.
        if (commands.size() > Config.getPref().getInt("undo.max", 1000)) {
            commands.removeFirst();
        }
        redoCommands.clear();
    }

    /**
     * Fires a commands change event after adding a command.
     * @param cmd command added
     * @since 13729
     */
    public void afterAdd(Command cmd) {
        if (cmd != null) {
            fireEvent(new CommandAddedEvent(this, cmd));
        }
        fireCommandsChanged();
    }

    /**
     * Fires a commands change event after adding a list of commands.
     * @param cmds commands added
     * @since 14381
     */
    public void afterAdd(List<? extends Command> cmds) {
        if (cmds != null) {
            for (Command cmd : cmds) {
                fireEvent(new CommandAddedEvent(this, cmd));
            }
        }
        fireCommandsChanged();
    }

    /**
     * Executes the command only if wanted and add it to the intern command queue.
     * @param c The command to execute. Must not be {@code null}.
     * @param execute true: Execute, else it is assumed that the command was already executed
     */
    public void add(final Command c, boolean execute) {
        addNoRedraw(c, execute);
        afterAdd(c);

    }

    /**
     * Executes the command and add it to the intern command queue.
     * @param c The command to execute. Must not be {@code null}.
     */
    public synchronized void add(final Command c) {
        addNoRedraw(c, true);
        afterAdd(c);
    }

    /**
     * Undoes the last added command.
     */
    public void undo() {
        undo(1);
    }

    /**
     * Undoes multiple commands.
     * @param num The number of commands to undo
     */
    public synchronized void undo(int num) {
        if (commands.isEmpty())
            return;
        GuiHelper.runInEDTAndWait(() -> {
            DataSet ds = OsmDataManager.getInstance().getEditDataSet();
            if (ds != null) {
                ds.beginUpdate();
            }
            try {
                for (int i = 1; i <= num; ++i) {
                    final Command c = commands.removeLast();
                    c.undoCommand();
                    redoCommands.addFirst(c);
                    fireEvent(new CommandUndoneEvent(this, c));
                    if (commands.isEmpty()) {
                        break;
                    }
                }
            } finally {
                if (ds != null) {
                    ds.endUpdate();
                }
            }
            fireCommandsChanged();
        });
    }

    /**
     * Redoes the last undoed command.
     */
    public void redo() {
        redo(1);
    }

    /**
     * Redoes multiple commands.
     * @param num The number of commands to redo
     */
    public synchronized void redo(int num) {
        if (redoCommands.isEmpty())
            return;
        for (int i = 0; i < num; ++i) {
            final Command c = redoCommands.removeFirst();
            c.executeCommand();
            commands.add(c);
            fireEvent(new CommandRedoneEvent(this, c));
            if (redoCommands.isEmpty()) {
                break;
            }
        }
        fireCommandsChanged();
    }

    /**
     * Fires a command change to all listeners.
     */
    private void fireCommandsChanged() {
        for (final CommandQueueListener l : listenerCommands) {
            l.commandChanged(commands.size(), redoCommands.size());
        }
    }

    private void fireEvent(CommandQueueEvent e) {
        preciseListenerCommands.forEach(e::fire);
    }

    /**
     * Resets the undo/redo list.
     */
    public void clean() {
        redoCommands.clear();
        commands.clear();
        fireEvent(new CommandQueueCleanedEvent(this, null));
        fireCommandsChanged();
    }

    /**
     * Resets all commands that affect the given dataset.
     * @param dataSet The data set that was affected.
     * @since 12718
     */
    public synchronized void clean(DataSet dataSet) {
        if (dataSet == null)
            return;
        boolean changed = false;
        changed |= commands.removeIf(c -> c.getAffectedDataSet() == dataSet);
        changed |= redoCommands.removeIf(c -> c.getAffectedDataSet() == dataSet);
        if (changed) {
            fireEvent(new CommandQueueCleanedEvent(this, dataSet));
            fireCommandsChanged();
        }
    }

    /**
     * Removes a command queue listener.
     * @param l The command queue listener to remove
     */
    public void removeCommandQueueListener(CommandQueueListener l) {
        listenerCommands.remove(l);
    }

    /**
     * Adds a command queue listener.
     * @param l The command queue listener to add
     * @return {@code true} if the listener has been added, {@code false} otherwise
     */
    public boolean addCommandQueueListener(CommandQueueListener l) {
        return listenerCommands.add(l);
    }

    /**
     * Removes a precise command queue listener.
     * @param l The precise command queue listener to remove
     * @since 13729
     */
    public void removeCommandQueuePreciseListener(CommandQueuePreciseListener l) {
        preciseListenerCommands.remove(l);
    }

    /**
     * Adds a precise command queue listener.
     * @param l The precise command queue listener to add
     * @return {@code true} if the listener has been added, {@code false} otherwise
     * @since 13729
     */
    public boolean addCommandQueuePreciseListener(CommandQueuePreciseListener l) {
        return preciseListenerCommands.add(l);
    }
}
