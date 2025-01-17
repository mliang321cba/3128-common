package common.utility.tester;

import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.ArrayList;

import common.utility.Log;
import common.utility.narwhaldashboard.NarwhalDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Subsystem;

/**
 * Team 3128's Tester utility class used to run system checks at competitions.
 */
public class Tester {
    
    /**System test */
    public static class UnitTest extends Command {

        protected String testName;
        protected Command command;
        protected BooleanSupplier passCondition;
        protected TestState testState;
        private boolean interrupted = false;

        /**
         * Creates a new Unit Test.
         * @param testName Name of the test.
         * @param command Command to be run for the test.
         */
        public UnitTest(String testName, Command command) {
            this(testName, command, ()-> true);
        }

        /**
         * Creates a new Unit Test.
         * @param testName Name of the test.
         * @param command Command to be run for the test.
         * @param passCondition Condition for the test to pass.
         */
        public UnitTest(String testName, Command command, BooleanSupplier passCondition) {
            this.testName = testName;
            this.command = command.handleInterrupt(()-> interrupted = true);
            this.passCondition = passCondition;
            testState = TestState.FAILED;
            for (final Subsystem subsystem : command.getRequirements()) {
                addRequirements(subsystem);
            }
        }

        @Override
        public void initialize() {
            Log.info(testName, "Test Running");
            command.initialize();
            interrupted = false;
            testState = TestState.RUNNING;
        }

        @Override
        public void execute() {
            command.execute();
        }

        @Override
        public void end(boolean interrupted) {
            testState = (this.interrupted || interrupted || !passCondition.getAsBoolean()) ? TestState.FAILED : TestState.PASSED;
            command.end(interrupted);
        }

        @Override
        public boolean isFinished() {
            return command.isFinished() || !command.isScheduled();
        }
    }

    /**Collection of tests to be run for a system */
    public class Test extends Command {
        private final ArrayList<UnitTest> unitTests;
        private final String name;
        private TestState state;
        private int curIndex;

        /**
         * Creates a test for a specific system.
         * @param name Name of the test or system.
         */
        private Test(String name) {
            unitTests = new ArrayList<UnitTest>();
            this.name = name;
            state = TestState.FAILED;
            curIndex = 0;
            NarwhalDashboard.getInstance().addUpdate(name, ()-> state);
            NarwhalDashboard.getInstance().addButton(name, (boolean pressed) -> {
                if (pressed) this.schedule();
            });
        }

        /**
         * Adds a unit test to be run.
         * @param test A unit test for the system.
         */
        public void addTest(UnitTest test) {
            unitTests.add(test);
        }

        @Override
        public void initialize() {
            curIndex = 0;
            state = TestState.RUNNING;
            Log.info(name, "TEST RUNNING");
            if (unitTests.size() == 0) state = TestState.FAILED;
            else unitTests.get(0).schedule();
        }

        @Override
        public void execute() {
            if (unitTests.size() == 0) return;

            final UnitTest test = unitTests.get(curIndex);
            switch(test.testState) {
                case FAILED:
                    state = TestState.FAILED;
                    return;
                case PASSED:
                    curIndex ++;
                    if (curIndex == unitTests.size()) {
                        state = TestState.PASSED;
                        return;
                    }
                    unitTests.get(curIndex).schedule();
                    break;
                default:
            }
        }

        @Override
        public void end(boolean interrupted) {
            Log.info(name, "TEST " + state);
        }

        @Override
        public boolean isFinished() {
            return state != TestState.RUNNING;
        }

        public TestState getTestState() {
            return state;
        }
    }

    /**Enum representing test states */
    public enum TestState {
        FAILED,
        RUNNING,
        PASSED
    }

    private static Tester instance;

    public static synchronized Tester getInstance() {
        if (instance == null) {
            instance = new Tester();
        }
        return instance;
    }

    private Tester() {}

    public HashMap<String, Test> systemTests = new HashMap<String, Test>();

    /**
     * Adds a unit test to be run for a system.
     * @param name Name of the test or system.
     * @param test Unit test to be added.
     */
    public void addTest(String name, UnitTest test) {
        if (!systemTests.containsKey(name)) {
            systemTests.put(name, new Test(name));
        }
        systemTests.get(name).addTest(test);
    }

    /**
     * Runs the unit tests for a system.
     * @param name Name of the test or system.
     */
    public void runTest(String name) {
        systemTests.get(name).schedule();
    }
}
