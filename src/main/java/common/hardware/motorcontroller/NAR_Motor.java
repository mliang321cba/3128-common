package common.hardware.motorcontroller;

import java.util.HashSet;
import java.util.LinkedList;

import common.core.NAR_Robot;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.motorcontrol.MotorController;

/**
 * Team 3128's motor class replacement for {@link MotorController}
 * @since Charged Up 2023
 * @author Mason Lam
 */
public abstract class NAR_Motor {
    private static final HashSet<NAR_Motor> leaders = new HashSet<NAR_Motor>();

    static {
        NAR_Robot.addPeriodic(()-> {
            for (final NAR_Motor leader : leaders) {
                final double output = leader.getAppliedOutput();
                for (final NAR_Motor follower : leader.followers) {
                    follower.set(output);
                }
            }
        }, 0.1);
    }

    /**
     * Motor control modes
     */
    public enum Control {
        PercentOutput,
        Velocity,
        Position;
    }

    /**
     * Motor states when no voltage is applied
     */
    public enum Neutral {
        BRAKE,
        COAST
    }

    private final LinkedList<NAR_Motor> followers = new LinkedList<NAR_Motor>();
    private double prevValue = 0;
    private Control prevMode = Control.PercentOutput;
    private double prevFeedForward = 0;
    private double minInput;
    private double maxInput;
    private boolean isContinuous = false;
    protected double unitConversionFactor = 1;
    protected double timeConversionFactor = 1;

    /**
     * Sets motor output power in volts
     * @param volts The voltage of the motor from -12 to 12
     */
    public void setVolts(double volts) {
        set(volts / 12.0);
    }

    /**
     * Sets motor output power
     * @param output The speed of the motor from -1 to 1
     */
    public void set(double output) {
        set(output, Control.PercentOutput);
    }

    /**
     * Sets the motor state
     * @param value Output of motor dependent on control mode
     * @param mode Type of control mode
     */
    public void set(double value, Control mode) {
        set(value, mode, 0);
    }

    /**
     * Sets the motor state with feedforward
     * @param value Output of motor dependent on control mode
     * @param mode Type of control mode
     * @param feedForward Feedforward of motor measured in volts
     */
    public void set(double value, Control mode, double feedForward) {
        if (value == prevValue && mode == prevMode && feedForward == prevFeedForward) return;
        prevValue = value;
        prevMode = mode;
        prevFeedForward = feedForward;
        switch(mode) {
            case PercentOutput:
                setPercentOutput(MathUtil.clamp(value, -1, 1));
                break;
            case Velocity:
                setVelocity(value / unitConversionFactor * timeConversionFactor, feedForward);
                break;
            case Position:
                final double position = value / unitConversionFactor;
                setPosition(isContinuous ? MathUtil.inputModulus(position, minInput, maxInput) : position, feedForward);
                break;
        }
    }

    /** Enables continuous input.
    *
    * <p>Rather then using the max and min input range as constraints, the motor considers them to be the
    * same point and automatically calculates the shortest route to the setpoint.
    * <p> WARNING: Do not use with onBoard PID control with CTRE devices, works with SparkMax.
    *
    * @param minInput The minimum value expected from the input.
    * @param maxInput The maximum value expected from the input.
    */
    public void enableContinuousInput(double minInput, double maxInput) {
        this.minInput = Math.min(minInput, maxInput);
        this.maxInput = Math.max(minInput, maxInput);
        isContinuous = true;
    }

    /**
     * Changes the units the motor measures position in, ie. rotations degrees
     * @param conversionFactor Conversion factor to change position units
     */
    public void setUnitConversionFactor(double conversionFactor) {
        this.unitConversionFactor = conversionFactor;
    }

    /**
     * Changes the units the motor measures time/velocity in, ie. rotations per minute rotations per second
     * @param conversionFactor Conversion factor to change time units
     */
    public void setTimeConversionFactor(double conversionFactor) {
        this.timeConversionFactor = conversionFactor;
    }

    /**
     * Resets the motor position
     * @param position Motor position, default units - rotations
     */
    public void resetPosition(double position) {
        resetRawPosition(position / unitConversionFactor);
    }

    /**
     * Sets the motor inverted
     * @param inverted Inverts the motor
     */
    public abstract void setInverted(boolean inverted);

    /**
     * Sets motor output power
     * @param speed The speed of the motor from -1 to 1
     */
    protected abstract void setPercentOutput(double speed);

    /**
     * Sets the motor output based on its velocity
     * @param rpm Velocity of the motor in RPM
     * @param feedForward Feedforward component measured in volts
     */
    protected abstract void setVelocity(double rpm, double feedForward);

    /**
     * Sets motor output based on its position
     * @param rotations Position of the motor in rotations
     * @param feedForward Feedforward component measured in volts
     */
    protected abstract void setPosition(double rotations, double feedForward);

    /**
     * Resets the motor position
     * @param rotations Number of rotations
     */
    protected abstract void resetRawPosition(double rotations);

    /**
     * Gets the motors current output
     * @return Double measuring percent output from -1 to 1
     */
    public abstract double getAppliedOutput();

    /**
     * Returns the current going to the motor, increasing values means the motor is stalling
     * @return Double measuring the stall current of the motor
     */
    public abstract double getStallCurrent();

    /**
     * Returns the current motor position, default unit - rotations
     * @return Double measuring motor position
     */
    public double getPosition() {
        final double position = getRawPosition() * unitConversionFactor;
        return isContinuous ? MathUtil.inputModulus(position, minInput, maxInput) : position;
    }

    /**
     * Returns the current motor velocity, default unit - RPM
     * @return Double measuring motor velocity
     */
    public double getVelocity() {
        return getRawVelocity() * unitConversionFactor / timeConversionFactor;
    }

    /**
     * Returns the motor position in rotations
     * @return Double measuring motor position
     */
    protected abstract double getRawPosition();

    /**
     * Returns the current motor velocity in RPM
     * @return Double measuring motor velocity
     */
    protected abstract double getRawVelocity();

    /**
     * Sets a motor's output based on the leader's
     * @param leader The motor to follow
     */
    public void follow(NAR_Motor leader) {
        leader.followers.add(this);
        leaders.add(leader);
    }

    /**
     * Sets the motor's idle mode, its behavior when no voltage is applied
     * @param mode Type of idle mode
     */
    public void setNeutralMode(Neutral mode) {
        switch(mode) {
            case BRAKE:
                setBrakeMode();
                break;
            case COAST:
                setCoastMode();
                break;
        }
    }

    /**
     * Sets the motor in brake mode
     */
    protected abstract void setBrakeMode();

    /**
     * Sets the motor in coast mode
     */
    protected abstract void setCoastMode();

    /**
     * Sets voltage compensation, keeps output consistent when battery is above x volts
     * @param volts The max volts the motor goes too
     */
    public abstract void enableVoltageCompensation(double volts);

    public abstract void setCurrentLimit(int limit);

    /**
     * Set the status frame rate to Team 3128's defaults
     */
    public abstract void setDefaultStatusFrames();

    /**
     * Returns the motor object controlling the motion
     * @return The motor ie. SparkMax, TalonFX
     */
    public abstract MotorController getMotor();
}