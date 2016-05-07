package reflow;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;

public class Profile {
	
	private double[] time;
	private double[] temperature;
	private int num_points;
	private String name;
	private String filename;
	
	public Profile() {
		num_points = 0;
		name = "";
	}
	
	public boolean save(String filename) {
		this.filename = filename;
		try {
			PrintWriter writer = new PrintWriter(filename, "UTF-8");
			writer.println(name);
			writer.format("%d\n", num_points);
			for (int c = 0; c < num_points; c++)
				writer.format("%f %f\n", time[c], temperature[c]);
			writer.close();
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public boolean load(String filename) {
// TODO Implement file validations
		this.filename = filename;
		File file = new File(filename);
		try {
			Scanner scanner = new Scanner(file);
			name = scanner.nextLine();
			num_points = scanner.nextInt();
			temperature = new double[num_points];
			time = new double[num_points];
			
			for (int c = 0; c < num_points; c++) {
				time[c] = scanner.nextDouble();
				temperature[c] = scanner.nextDouble();
			}

			scanner.close();
			
		} catch (Exception e) {
			return false;
		}
		return true;
	}
	
	public String getFilename() {
		return filename;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getNumPoints() {
		return num_points;
	}
	
	public double[] getTimes() {
		return time;
	}
	
	public double[] getTemperatures() {
		return temperature;
	}
	
	public double getTimePoint(int i) {
		if (i >= num_points)
			return 0;
		return time[i];
	}

	public double getTemperaturePoint(int i) {
		if (i >= num_points)
			return 0;
		return temperature[i];
	}
	
	public double getMaxTime() {
		return num_points == 0 ? 0 : time[num_points - 1];
	}
	
	public double getTemperature(double t) {
		int c;
		double prev_time, prev_temp;
		
		if (num_points == 0)
			return 0;
		
		for (c = 0; c < num_points; c++) 
			if (time[c] >= t)
				break;
		
		if (c == num_points)
			return temperature[num_points - 1];
	
		if (time[c] == t)
			return temperature[c];
		
		if (c == 0) {			// Assume point (0,0) if not specified
			prev_time = 0;
			prev_temp = 0;
		} else {
			prev_time = time[c-1];
			prev_temp = temperature[c-1];
		}
		
		return (t - prev_time) * (temperature[c] - prev_temp) / (time[c] - prev_time) + prev_temp;
	}
}
