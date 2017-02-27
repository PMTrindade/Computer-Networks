package player;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class Viewer {
	private JFrame mainFrame;
	private _JPanel panel;

	private QualityMetrics stats;

	public Viewer(int width, int height) {
		init();
		mainFrame.setSize(width, height);
		stats = new QualityMetrics();
	}

	public BufferedImage updateFrame(byte[] imgData, int length) {
		ByteArrayInputStream bais = new ByteArrayInputStream(imgData, 0, length);

		try {
			BufferedImage img = ImageIO.read(bais);
			panel.setImage(img, length);
			ByteBuffer bb = ByteBuffer.wrap(imgData, length - 12, 12);
			stats.updateStats(img.getHeight(), bb.getInt(), bb.getLong());
			return img;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void init() {
		mainFrame = new JFrame("Video Viewer");
		mainFrame.setLayout(new GridLayout(1, 1));
		mainFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent windowEvent) {
				System.exit(0);
			}
		});

		panel = new _JPanel();

		mainFrame.add(panel);
		mainFrame.setVisible(true);
	}

	class _JPanel extends JPanel {
		private static final long serialVersionUID = 1L;

		Image img;

		public void paintComponent(Graphics g) {
			Graphics2D g2d = (Graphics2D) g;
			super.paintComponent(g2d);
			if (img != null) {
				g2d.drawImage(img, 0, 0, super.getWidth(), super.getHeight(), null);

				g2d.setColor(Color.GREEN);
				g2d.setFont(g2d.getFont().deriveFont(18.0f));
				g2d.drawString(String.format("(%dp, %d, %.1f)", stats.avgQuality(), stats.droppedFrames(), stats.accumJitter()), 10, super.getHeight() - 30);
			}
		}

		void setImage(Image img, int length) {
			this.img = img;
			repaint();
		}
	}
}

class QualityMetrics {
	private int totalFrames = 0;

	private int lastFrame = -1;
	private int droppedFrames = 0;

	private long t0 = -1, ts0;
	private double accumJitter = 0.0;

	private double sumVResolution = 0.0;

	void updateStats(int yres, int frame, long ts) {
		long now = System.nanoTime();

		sumVResolution += yres;

		totalFrames++;
		if (frame != lastFrame + 1)
			droppedFrames++;
		lastFrame = frame;

		double delay = Math.abs((ts - ts0) - (System.nanoTime() - t0)) / 1e9;

		if (t0 > 0 && delay > 1 / 30.0)
			accumJitter += delay;

		t0 = now;
		ts0 = ts;
	}

	int avgQuality() {
		return (int) (sumVResolution / totalFrames);
	}

	int droppedFrames() {
		return droppedFrames;
	}

	double accumJitter() {
		return accumJitter;
	}
}