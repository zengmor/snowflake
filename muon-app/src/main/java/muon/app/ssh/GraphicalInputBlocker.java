/**
 * 
 */
package muon.app.ssh;

import lombok.extern.slf4j.Slf4j;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * @author subhro
 *
 */
@Slf4j
public class GraphicalInputBlocker extends JDialog implements InputBlocker {
	private JFrame window;

	/**
	 * 
	 */
	public GraphicalInputBlocker(JFrame window) {
		super(window);
		this.window = window;
		setModal(true);
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setSize(400, 300);
	}

	@Override
	public void blockInput() {
		SwingUtilities.invokeLater(() -> {
			//System.out.println("Making visible...");
			log.info("Making visible...");
			this.setLocationRelativeTo(window);
			this.setVisible(true);
		});
	}

	@Override
	public void unblockInput() {
		SwingUtilities.invokeLater(() -> {
			this.setVisible(false);
		});
	}

}
