
public class Command<R> {

    /** The receiving object. */
    protected final R receiver;

    /** Execution state. */
    private boolean executed;

    /**
     * Constructs a command for a given receiver.
     *
     * @param receiver  the given receiver
     * @pre {@code receiver != null}
     */
    public Command(final R receiver)
            throws NullPointerException {
        if (receiver == null) {
            throw new NullPointerException("Command(R).pre violated: "
                    + "receiver == null");
        }
        this.receiver = receiver;
        this.executed = false;
    }

    /**
     * Gets execution status of this command.
     *
     * @return execution status
     */
    public boolean isExecuted() {
        return executed;
    }

    /**
     * Executes the command.
     * A concrete command will override this method.
     *
     * @throws IllegalStateException  if {@code executed}
     * @pre {@code ! executed && }
     *   precondition of the command holds in the receiver
     * @post {@code executed}
     */
    public void execute() throws IllegalStateException {
        if (executed) {
            throw new IllegalStateException("Command.execute().pre violated: "
                    + "command was already executed");
        }
        executed = true;
    }

    /**
     * Undoes the command.
     * A concrete command will override this method.
     *
     * @pre {@code executed && }
     *   precondition of the undo holds in the receiver
     * @post {@code ! executed}
     */
    public void undo() throws IllegalStateException {
        if (! executed) {
            throw new IllegalStateException("Command.undo().pre violated: "
                    + "command was not yet executed");
        }
        executed = false;
    }

}
