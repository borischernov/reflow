package reflow;

public class PidController {
	
	public enum State {CS_IDLE, CS_WORKING}
	
	private long start_time;
	private State state;
	
	double Kp, Ki, Kd;
	
	long prev_time;
	int prev_pwr;
	double prev_e;				// Previous value of error;
	double int_e;				// Integral of error
	
	double e_max;
	double e_min;
	double e_sq_int;
	
	public PidController(double p, double i, double d) {
		Kp = p;
		Ki = i;
		Kd = d;
		state = State.CS_IDLE;
	}
	
	public State getState() {
		return state;
	}
	
	public void start() {
		if (state != State.CS_IDLE) 
			return;
		
		state = State.CS_WORKING;
		start_time = System.currentTimeMillis();
		prev_time = start_time;
		prev_pwr = 0;
		int_e = 0;
		prev_e = 0;
		e_max = 0;
		e_min = 0;
		e_sq_int = 0;
	}
	
	public void stop() {
		if (state != State.CS_WORKING) 
			return;
		
		state = State.CS_IDLE;
	}
	
	public long getTime() {
		if (state != State.CS_WORKING) 
			return 0;

		return (System.currentTimeMillis() - start_time) / 1000;
	}
	
	public double getMinError() {
		return e_min;
	}
	
	public double getMaxError() {
		return e_max;
	}

	public double getIntegralError() {
		return e_sq_int;
	}
	
	public int iteration(double current_temp, double profile_temp) {
		double e = profile_temp - current_temp;
		long curr_time = System.currentTimeMillis();
		double dt = (double)(curr_time - prev_time) / 1000.0;
		
		int_e += (prev_e + e) / 2 * dt;
		double de = (e - prev_e) / dt;
		
		double u = Kp * e + Ki * int_e + Kd * de;
		
		if (e > 0 && e_max < e)
			e_max = e;

		if (e < 0 && e_min > e)
			e_min = e;
		
		e_sq_int += ((e * e) + (prev_e * prev_e)) / 2 * dt;

		prev_e = e;
		prev_time = curr_time;
		prev_pwr += u;
		
		if (prev_pwr < 0)
			prev_pwr = 0;
		
		if (prev_pwr > 100)
			prev_pwr = 100;
		
		return prev_pwr;
	}
	
}