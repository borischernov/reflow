package reflow;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.DisposeEvent;

import reflow.ReflowController.Command;
import reflow.ReflowController.CommandCallback;

import org.eclipse.swt.widgets.Label;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.xygraph.dataprovider.CircularBufferDataProvider;
import org.eclipse.nebula.visualization.xygraph.dataprovider.ISample;
import org.eclipse.nebula.visualization.xygraph.dataprovider.Sample;
import org.eclipse.nebula.visualization.xygraph.figures.IXYGraph;
import org.eclipse.nebula.visualization.xygraph.figures.Trace;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.nebula.visualization.xygraph.figures.Trace.PointStyle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.ProgressBar;

public class MainWindow {

	private final String config_file = "reflow.properties";
	
	private static ReflowController controller = null;
	private static PidController pid = null;
	private Profile profile;

	private static Properties config;
	
	protected Shell shell;
	private Text txtPortName;
	private Label lblTemperature;
	private Label lblOven;
	private Scale scOven;
	private Button btnStart;
	private Button btnConnect;
	private CircularBufferDataProvider temperatureDataProvider;
	private CircularBufferDataProvider profileDataProvider;	
	private IXYGraph xyGraph;
	private Trace profileTrace;
	private Trace temperatureTrace;
	private Label lblTarget;
	private Text txtP;
	private Text txtI;
	private Text txtD;
	private Group grpReflow;
	private Button btnLoadProfile;
	private Label lblPort;
	private Label lblP;
	private Label lblI;
	private Label lblD;
	private Label lblCurrent;
	private Label lblTargetLbl;
	private Label lblPower;
	private Label lblBrightness;
	private Label lblContrast;
	private Label lblNewLabel;
	private Spinner spTempOffset;
	private Scale scContrast;
	private Scale scBrightness;
	private ProgressBar progressBar;

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			MainWindow window = new MainWindow();
			window.open();

			if (controller != null) 
				controller.disconnect();

		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	/**
	 * Open the window.
	 */
	public void open() {
		Display display = Display.getDefault();
		createContents();
		readConfig();
		shell.open();
		shell.layout();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/********************************************
	 * Read configuration from properties file
	 */
	
	void readConfig() {
		config = new Properties();
		try {
			FileInputStream input = new FileInputStream(config_file);
			config.load(input);
		} catch (Exception ex) {
			System.err.println("Error loading configuration data.");
		}

		txtPortName.setText(config.getProperty("controller.port", "/dev/rfcomm1"));
		txtP.setText(config.getProperty("pid.p", "1"));
		txtI.setText(config.getProperty("pid.i", "0"));
		txtD.setText(config.getProperty("pid.d", "0"));

		load_profile(config.getProperty("profile.default", "lead-free.pfl"));
	}
	
	/********************************************
	 * Store configuration to properties file
	 */
	
	void saveConfig() {
		config.setProperty("controller.port", txtPortName.getText());
		config.setProperty("pid.p", txtP.getText());
		config.setProperty("pid.i", txtI.getText());
		config.setProperty("pid.d", txtD.getText());
		config.setProperty("profile.default", profile.getFilename());
		
		try {
			FileOutputStream out = new FileOutputStream(config_file);
	        config.store(out, null);
	        out.close();
		} catch (Exception ex) {
			System.err.println("Error saving configuration data.");
		}
	}
	
	/********************************************
	 * Stop PID controller
	 */
	
	private void stopPid() {
		pid.stop();
		setOven(0);
		btnStart.setText("Start");
		btnLoadProfile.setEnabled(true);
		lblTarget.setText("");
	}
	
	/********************************************
	 * Process temperature measurement result.
	 */
	protected void processTemperature() {
		int temp = controller.getTemperature();
		lblTemperature.setText(Integer.toString(temp) + "°C");
		
		if (pid == null || pid.getState() != PidController.State.CS_WORKING)
			return;
		
		double time = pid.getTime();
		if (time > profile.getMaxTime()) {
			stopPid();
			return;
		}
		
		double target = profile.getTemperature(time);
		lblTarget.setText(Integer.toString((int)target) + "°C");
		
		int pwr = pid.iteration(temp, target);
		setOven(pwr);
		
	    temperatureDataProvider.addSample(new Sample(time, temp));
	    xyGraph.performAutoScale();
	    
	    progressBar.setSelection((int)(time * 100 / profile.getMaxTime()));
	}
	
	/********************************************
	 * Controller connect / disconnect
	 */
	protected void toggleControllerLink() {
		if (controller == null) {
			try {
				controller = new ReflowController(txtPortName.getText());
			} catch (IOException e) {
				System.err.println("Error opening serial port");
				MessageBox alert = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
			    alert.setText("Error");
			    alert.setMessage("Error opening serial port");
			    alert.open();
			    return;
			}
			controller.setCommandCallback(new CommandCallback() {
				@Override
				void onResponse(Command cmd) {
					if (cmd == Command.CMD_READ_SETTINGS) {
						Display.getDefault().asyncExec(new Runnable() {
							   @Override
							   public void run() {
								   spTempOffset.setSelection(controller.getSensorOffset());
								   scBrightness.setSelection(controller.getLcdBacklight());
								   scContrast.setSelection(controller.getLcdContrast());
								   spTempOffset.setEnabled(true);
								   scBrightness.setEnabled(true);
								   scContrast.setEnabled(true);
							   }
						});
					} else if (cmd == Command.CMD_READ_TEMPERATURE) {
						Display.getDefault().asyncExec(new Runnable() {
							   @Override
							   public void run() {
								   processTemperature();
							   }
						});
					}
				}
			});
			btnConnect.setText("Disconnect");
			btnStart.setEnabled(true);
			scOven.setEnabled(true);
			setOven(0);
		} else {
			controller.disconnect();
			controller = null;
			if (pid != null && pid.getState() == PidController.State.CS_WORKING) {
				stopPid();
			}
			btnConnect.setText("Connect");
			btnStart.setEnabled(false);
			scOven.setEnabled(false);
			lblOven.setText("");
			lblTemperature.setText("");
			spTempOffset.setEnabled(false);
			scBrightness.setEnabled(false);
			scContrast.setEnabled(false);
		}
		progressBar.setSelection(0);
	}
	
	/********************************************
	 * Start / Stop reflow process
	 */
	protected void toggleReflow() {
		if (pid != null && pid.getState() == PidController.State.CS_WORKING) {
			stopPid();
			return;
		}
		
		pid = new PidController(
					Double.parseDouble(txtP.getText()),
					Double.parseDouble(txtI.getText()),
					Double.parseDouble(txtD.getText())
				);
		pid.start();
		btnStart.setText("Stop");
		btnLoadProfile.setEnabled(false);

		if (temperatureDataProvider == null) {
			temperatureDataProvider = new CircularBufferDataProvider(false);
			temperatureDataProvider.setBufferSize(500);

			temperatureTrace = new Trace("", xyGraph.getPrimaryXAxis(), xyGraph.getPrimaryYAxis(), temperatureDataProvider);
			temperatureTrace.setPointStyle(PointStyle.NONE);

			xyGraph.addTrace(temperatureTrace);
		} else {
			temperatureDataProvider.clearTrace();
		}
	}
	
	/********************************************
	 * Set oven power
	 */
	protected void setOven(int oven_pct) {
		scOven.setSelection(oven_pct);
        lblOven.setText(oven_pct + "%");
        if (controller != null) {
      	  controller.setDutyCycle(oven_pct);
        }
		
	}

	/********************************************
	 * Load profile from disk
	 */
	protected void load_profile(String filename) {
		profile = new Profile();
		if (!profile.load(filename))
			return;

		if (profileDataProvider == null)
			profileDataProvider = new CircularBufferDataProvider(false);

		profileDataProvider.setBufferSize(profile.getNumPoints());
		profileDataProvider.setCurrentXDataArray(profile.getTimes());
		profileDataProvider.setCurrentYDataArray(profile.getTemperatures());

		if (profileTrace == null) {
			profileTrace = new Trace("", xyGraph.getPrimaryXAxis(), xyGraph.getPrimaryYAxis(), profileDataProvider);
			profileTrace.setPointStyle(PointStyle.NONE);
			xyGraph.addTrace(profileTrace);
		}
		xyGraph.performAutoScale();
		xyGraph.setTitle(profile.getName());
	}
	
	/********************************************
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell();
		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent arg0) {
				saveConfig();
			}
		});
		shell.setSize(540, 556);
		shell.setText("Reflow Controller");
		
		Canvas canvas = new Canvas(shell, SWT.NONE);
		canvas.setBounds(10, 10, 520, 249);

		// use LightweightSystem to create the bridge between SWT and draw2D
		final LightweightSystem lws = new LightweightSystem(canvas);
		
		btnLoadProfile = new Button(canvas, SWT.NONE);
		btnLoadProfile.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				   FileDialog dialog = new FileDialog(shell, SWT.OPEN);
				   dialog.setFilterExtensions(new String [] {"*.pfl"});
				   String file = dialog.open(); 
				   if (file != null)
					   load_profile(file);					
			}
		});
		btnLoadProfile.setBounds(423, 0, 97, 29);
		btnLoadProfile.setText("LoadProfile");
		
		Group grpController = new Group(shell, SWT.NONE);
		grpController.setText("Controller");
		grpController.setBounds(10, 264, 160, 250);
		
		txtPortName = new Text(grpController, SWT.BORDER);
		txtPortName.setLocation(10, 26);
		txtPortName.setSize(118, 27);
		
		btnConnect = new Button(grpController, SWT.NONE);
		btnConnect.setLocation(10, 59);
		btnConnect.setSize(97, 29);
		btnConnect.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
					toggleControllerLink();
			}
		});
		btnConnect.setText("Connect");
		
		lblPort = new Label(grpController, SWT.NONE);
		lblPort.setBounds(10, 3, 74, 17);
		lblPort.setText("Port");
		
		scBrightness = new Scale(grpController, SWT.NONE);
		scBrightness.setEnabled(false);
		scBrightness.setBounds(10, 118, 136, 27);
		scBrightness.addListener(SWT.Selection, new Listener() {
	        public void handleEvent(Event event) {
	          controller.setLcdBacklight(scBrightness.getSelection());
	        }
        });
		
		scContrast = new Scale(grpController, SWT.NONE);
		scContrast.setEnabled(false);
		scContrast.setBounds(10, 164, 136, 27);
		scContrast.addListener(SWT.Selection, new Listener() {
	        public void handleEvent(Event event) {
	          controller.setLcdContrast(scContrast.getSelection());
	        }
        });
		
		spTempOffset = new Spinner(grpController, SWT.BORDER);
		spTempOffset.setEnabled(false);
		spTempOffset.setMaximum(50);
		spTempOffset.setMinimum(-50);
		spTempOffset.setBounds(88, 197, 58, 27);
		spTempOffset.addListener(SWT.Selection, new Listener() {
	        public void handleEvent(Event event) {
	          controller.setSensorOffset(spTempOffset.getSelection());
	        }
        });
		
		lblBrightness = new Label(grpController, SWT.NONE);
		lblBrightness.setBounds(10, 97, 136, 17);
		lblBrightness.setText("LCD Brightness");
		
		lblContrast = new Label(grpController, SWT.NONE);
		lblContrast.setText("LCD Contrast");
		lblContrast.setBounds(10, 148, 136, 17);
		
		lblNewLabel = new Label(grpController, SWT.NONE);
		lblNewLabel.setFont(SWTResourceManager.getFont("Ubuntu", 9, SWT.NORMAL));
		lblNewLabel.setBounds(10, 200, 74, 17);
		lblNewLabel.setText("Temp. Offset");
		
		grpReflow = new Group(shell, SWT.NONE);
		grpReflow.setText("Reflow");
		grpReflow.setBounds(180, 264, 350, 250);
		
		Label lblPowerLbl = new Label(grpReflow, SWT.NONE);
		lblPowerLbl.setBounds(10, 100, 74, 17);
		lblPowerLbl.setText("Power");
		
		lblTargetLbl = new Label(grpReflow, SWT.NONE);
		lblTargetLbl.setText("Target");
		lblTargetLbl.setFont(SWTResourceManager.getFont("Ubuntu", 7, SWT.NORMAL));
		lblTargetLbl.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblTargetLbl.setBounds(190, 51, 50, 10);
		
		lblCurrent = new Label(grpReflow, SWT.NONE);
		lblCurrent.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblCurrent.setFont(SWTResourceManager.getFont("Ubuntu", 7, SWT.NORMAL));
		lblCurrent.setBounds(190, 0, 50, 10);
		lblCurrent.setText("Current");
		
		lblPower = new Label(grpReflow, SWT.NONE);
		lblPower.setText("Power");
		lblPower.setFont(SWTResourceManager.getFont("Ubuntu", 7, SWT.NORMAL));
		lblPower.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblPower.setBounds(190, 103, 50, 10);
		
		txtP = new Text(grpReflow, SWT.BORDER);
		txtP.setLocation(10, 18);
		txtP.setSize(37, 27);
		
		txtI = new Text(grpReflow, SWT.BORDER);
		txtI.setLocation(53, 18);
		txtI.setSize(37, 27);
		
		txtD = new Text(grpReflow, SWT.BORDER);
		txtD.setLocation(98, 18);
		txtD.setSize(37, 27);
		
		btnStart = new Button(grpReflow, SWT.NONE);
		btnStart.setLocation(10, 59);
		btnStart.setSize(97, 29);
		btnStart.setEnabled(false);
		btnStart.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				toggleReflow();
			}
		});
		btnStart.setText("Start");
		
		lblOven = new Label(grpReflow, SWT.NONE);
		lblOven.setAlignment(SWT.RIGHT);
		lblOven.setFont(SWTResourceManager.getFont("Ubuntu", 28, SWT.NORMAL));
		lblOven.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblOven.setLocation(190, 103);
		lblOven.setSize(145, 45);
		
		lblTarget = new Label(grpReflow, SWT.NONE);
		lblTarget.setLocation(190, 51);
		lblTarget.setSize(145, 45);
		lblTarget.setForeground(SWTResourceManager.getColor(SWT.COLOR_LINK_FOREGROUND));
		lblTarget.setFont(SWTResourceManager.getFont("Ubuntu", 28, SWT.NORMAL));
		lblTarget.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblTarget.setAlignment(SWT.RIGHT);
		
		scOven = new Scale(grpReflow, SWT.NONE);
		scOven.setEnabled(false);
		scOven.setLocation(10, 125);
		scOven.setSize(152, 27);
		scOven.addListener(SWT.Selection, new Listener() {
	        public void handleEvent(Event event) {
		          setOven(scOven.getSelection());
		        }
	        });
		
		lblTemperature = new Label(grpReflow, SWT.NONE);
		lblTemperature.setLocation(190, 0);
		lblTemperature.setSize(145, 45);
		lblTemperature.setBackground(SWTResourceManager.getColor(SWT.COLOR_WHITE));
		lblTemperature.setFont(SWTResourceManager.getFont("Ubuntu", 28, SWT.NORMAL));
		lblTemperature.setAlignment(SWT.RIGHT);
		
		lblP = new Label(grpReflow, SWT.NONE);
		lblP.setBounds(20, 0, 16, 17);
		lblP.setText("P");
		
		lblI = new Label(grpReflow, SWT.NONE);
		lblI.setText("I");
		lblI.setBounds(68, 0, 16, 17);
		
		lblD = new Label(grpReflow, SWT.NONE);
		lblD.setText("D");
		lblD.setBounds(108, 0, 16, 17);
		
		progressBar = new ProgressBar(grpReflow, SWT.NONE);
		progressBar.setBounds(10, 188, 325, 14);
		
		xyGraph = new XYGraph();
		xyGraph.setShowLegend(false);
		xyGraph.getPrimaryXAxis().setTitle("Time, s");
		xyGraph.getPrimaryYAxis().setTitle("Temperature");
		xyGraph.getPrimaryXAxis().setShowMajorGrid(true);
		xyGraph.getPrimaryYAxis().setShowMajorGrid(true);
		lws.setContents(xyGraph);

	}
}
