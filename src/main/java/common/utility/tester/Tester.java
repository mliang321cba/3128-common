package common.utility.tester;

import static edu.wpi.first.wpilibj2.command.Commands.*;
import java.util.function.BooleanSupplier;
import java.util.HashMap;
import java.util.ArrayList;

import common.utility.Log;
import common.utility.narwhaldashboard.NarwhalDashboard;
import edu.wpi.first.wpilibj2.command.CommandBase;

/**
 * Team 3128's Tester utility class used to run system checks at competitions.
 */
public class Tester {
    
    /**System test */
    public static class UnitTest {

        protected String testName;
        protected CommandBase command;
        protected BooleanSupplier condition;
        protected TestState testState;
        protected double timeOut;

        /**
         * Creates a unit test.
         * @param testName Name of the test.
         * @param command Command to run for the test.
         * @param condition Condition to see if the test passed.
         * @param timeOut Time the test has to run before failing.
         */
        public UnitTest(String testName, CommandBase command, BooleanSupplier condition, double timeOut) {
            this.testName = testName;
            this.command = command;
            this.condition = condition;
            this.timeOut = timeOut;
            testState = TestState.FAILED;
        }

        /**
         * Returns a command that runs the test.
         */
        public CommandBase runTest() {
            return sequence(
                runOnce(()-> testState = TestState.RUNNING),
                deadline(
                    waitUntil(condition).withTimeout(timeOut),
                    command
                ),
                runOnce(()-> {
                    testState = condition.getAsBoolean() ? TestState.PASSED : TestState.FAILED;
                    if (testState == TestState.PASSED) Log.info(testName, testName + "TEST PASSED");
                    else Log.info(testName, "TEST FAILED");
                })
            );
        }
    }

    /**Collection of tests to be run for a system */
    private class Test extends CommandBase {
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
            else unitTests.get(0).runTest().schedule();
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
                    unitTests.get(curIndex).runTest().schedule();
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