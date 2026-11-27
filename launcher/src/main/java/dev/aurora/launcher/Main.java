package dev.aurora.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/** Aurora Launcher: Minecraft 1.21.11 + Fabric + Aurora in one window, with nickname switching and settings. */
public final class Main {
	static final Color BG = new Color(0x0C0C0F);
	static final Color PANEL = new Color(0x131317);
	static final Color PANEL2 = new Color(0x1A1A20);
	static final Color TEXT = new Color(0xE8E8EE);
	static final Color DIM = new Color(0x8A8A94);
	static Color accent;

	private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private final Config cfg = Config.load();
	private final Path root = Config.home();
	private JFrame frame;
	private CardLayout cards;
	private JPanel content;
	private final JComboBox<String> nickBox = new JComboBox<>();
	private final DefaultListModel<String> nickModel = new DefaultListModel<>();
	private JLabel status;
	private Bar progress;
	private JTextArea log;
	private Btn playBtn;
	private volatile boolean busy;
	private boolean updatingNicks;

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new Main().show());
	}

	static Color hex(String h, Color def) {
		try {
			return new Color(Integer.parseInt(h.replace("#", ""), 16));
		} catch (Exception e) {
			return def;
		}
	}

	private void show() {
		accent = hex(cfg.accent, new Color(0xFF7A1A));
		try {
			UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		} catch (Exception ignored) {
			// default look
		}
		frame = new JFrame("Aurora Launcher");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(860, 540);
		frame.setMinimumSize(new Dimension(760, 480));
		frame.setLocationRelativeTo(null);
		JPanel rootPanel = new JPanel(new BorderLayout());
		rootPanel.setBackground(BG);

		cards = new CardLayout();
		content = new JPanel(cards);
		content.setBackground(BG);
		content.add(playPage(), "play");
		content.add(accountsPage(), "accounts");
		content.add(settingsPage(), "settings");

		rootPanel.add(sidebar(), BorderLayout.WEST);
		rootPanel.add(content, BorderLayout.CENTER);
		frame.setContentPane(rootPanel);
		refreshNicks();
		frame.setVisible(true);
	}

	// ------------------------------------------------------------------ widgets

	static final class Btn extends JComponent {
		private final String text;
		private final boolean primary;
		private boolean hover;
		private Runnable action = () -> { };
		private boolean enabledLook = true;

		Btn(String text, boolean primary) {
			this.text = text;
			this.primary = primary;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					if (enabledLook) {
						action.run();
					}
				}
			});
			setPreferredSize(new Dimension(primary ? 200 : 110, primary ? 46 : 32));
		}

		Btn onClick(Runnable r) {
			action = r;
			return this;
		}

		void setActive(boolean v) {
			enabledLook = v;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Color base = primary ? accent : PANEL2;
			if (!enabledLook) {
				base = new Color(0x2A2A30);
			} else if (hover) {
				base = primary ? accent.brighter() : new Color(0x26262E);
			}
			g.setColor(base);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
			g.setFont(getFont().deriveFont(Font.BOLD, primary ? 16f : 13f));
			g.setColor(primary && enabledLook ? new Color(0x14100C) : TEXT);
			int w = g.getFontMetrics().stringWidth(text);
			g.drawString(text, (getWidth() - w) / 2, getHeight() / 2 + g.getFontMetrics().getAscent() / 2 - 2);
			g.dispose();
		}
	}

	static final class Bar extends JComponent {
		private double value;

		Bar() {
			setPreferredSize(new Dimension(100, 10));
		}

		void set(double v) {
			value = Math.max(0, Math.min(1, v));
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(PANEL2);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
			g.setColor(accent);
			g.fillRoundRect(0, 0, (int) (getWidth() * value), getHeight(), 10, 10);
			g.dispose();
		}
	}

	static final class Card extends JPanel {
		Card(java.awt.LayoutManager lm) {
			super(lm);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = (Graphics2D) g0.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(PANEL);
			g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
			g.dispose();
		}
	}

	private static JLabel label(String t, int size, boolean bold, Color c) {
		JLabel l = new JLabel(t);
		l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
		l.setForeground(c);
		return l;
	}

	private static void style(JComponent c) {
		c.setBackground(PANEL2);
		c.setForeground(TEXT);
		c.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		if (c instanceof JTextField t) {
			t.setCaretColor(TEXT);
		}
	}

	private JPanel sidebar() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(new Color(0x09090C));
		p.setBorder(BorderFactory.createEmptyBorder(20, 16, 16, 16));
		p.setPreferredSize(new Dimension(190, 100));
		JLabel logo = label("aurora", 24, true, TEXT);
		p.add(logo);
		p.add(label("launcher", 12, false, DIM));
		p.add(Box.createVerticalStrut(24));
		p.add(nav("Play", "play"));
		p.add(Box.createVerticalStrut(8));
		p.add(nav("Accounts", "accounts"));
		p.add(Box.createVerticalStrut(8));
		p.add(nav("Settings", "settings"));
		p.add(Box.createVerticalGlue());
		p.add(label("Minecraft " + Installer.MC, 11, false, DIM));
		p.add(label("Fabric " + Installer.LOADER, 11, false, DIM));
		return p;
	}

	private JComponent nav(String text, String card) {
		Btn b = new Btn(text, false).onClick(() -> cards.show(content, card));
		b.setPreferredSize(new Dimension(158, 36));
		b.setMaximumSize(new Dimension(158, 36));
		b.setAlignmentX(0f);
		return b;
	}

	// ------------------------------------------------------------------ play

	private JPanel playPage() {
		JPanel page = new JPanel(new BorderLayout(0, 12));
		page.setBackground(BG);
		page.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

		Card hero = new Card(new BorderLayout(0, 10));
		JPanel top = new JPanel();
		top.setOpaque(false);
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(label("Aurora Client", 28, true, TEXT));
		top.add(label("Minecraft " + Installer.MC + "  ·  Fabric " + Installer.LOADER + "  ·  Aurora built in", 13, false, DIM));
		hero.add(top, BorderLayout.NORTH);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		row.setOpaque(false);
		row.add(label("Nickname", 13, false, DIM));
		nickBox.setPreferredSize(new Dimension(190, 32));
		style(nickBox);
		nickBox.addActionListener(e -> {
			if (!updatingNicks && nickBox.getSelectedItem() != null) {
				cfg.nick = (String) nickBox.getSelectedItem();
				cfg.save();
			}
		});
		row.add(nickBox);
		playBtn = new Btn("PLAY", true).onClick(this::play);
		row.add(playBtn);
		hero.add(row, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		bottom.setOpaque(false);
		status = label("Ready", 12, false, DIM);
		progress = new Bar();
		bottom.add(status, BorderLayout.NORTH);
		bottom.add(progress, BorderLayout.CENTER);
		hero.add(bottom, BorderLayout.SOUTH);
		page.add(hero, BorderLayout.NORTH);

		Card logCard = new Card(new BorderLayout());
		log = new JTextArea();
		log.setEditable(false);
		log.setBackground(PANEL);
		log.setForeground(new Color(0xB4B4BE));
		log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		JScrollPane sp = new JScrollPane(log);
		sp.setBorder(null);
		sp.getViewport().setBackground(PANEL);
		logCard.add(label("Game log", 12, true, DIM), BorderLayout.NORTH);
		logCard.add(sp, BorderLayout.CENTER);
		page.add(logCard, BorderLayout.CENTER);
		return page;
	}

	private void logLine(String s) {
		SwingUtilities.invokeLater(() -> {
			log.append(s + "\n");
			log.setCaretPosition(log.getDocument().getLength());
		});
	}

	private void setStatus(String text, double frac) {
		SwingUtilities.invokeLater(() -> {
			status.setText(text);
			progress.set(frac);
		});
	}

	private void play() {
		if (busy) {
			return;
		}
		if (cfg.nick == null || !NICK.matcher(cfg.nick).matches()) {
			setStatus("Pick a valid nickname first (3-16 letters, digits or _)", 0);
			return;
		}
		busy = true;
		playBtn.setActive(false);
		cfg.save();
		Thread t = new Thread(() -> {
			try {
				Installer inst = new Installer(root);
				Installer.Prepared prep = inst.prepare(cfg, this::setStatus, false);
				List<String> cmd = Game.command(cfg, prep, root);
				setStatus("Starting Minecraft...", 1.0);
				Process pr = Game.start(cmd, cfg.gameDirPath(), this::logLine);
				SwingUtilities.invokeLater(() -> {
					switch (cfg.afterLaunch) {
						case "Close launcher" -> System.exit(0);
						case "Hide launcher" -> frame.setVisible(false);
						default -> { }
					}
				});
				int code = pr.waitFor();
				setStatus("Game closed (exit code " + code + ")", 0);
				SwingUtilities.invokeLater(() -> frame.setVisible(true));
			} catch (Exception e) {
				logLine("ERROR: " + e);
				setStatus("Failed: " + e.getMessage(), 0);
			} finally {
				busy = false;
				playBtn.setActive(true);
			}
		}, "launch");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ accounts

	private void refreshNicks() {
		updatingNicks = true;
		nickBox.removeAllItems();
		nickModel.clear();
		for (String n : cfg.nicks) {
			nickBox.addItem(n);
			nickModel.addElement(n);
		}
		nickBox.setSelectedItem(cfg.nick);
		updatingNicks = false;
	}

	private static String randomNick() {
		String[] a = {"Swift", "Dark", "Frost", "Neon", "Toxic", "Silent", "Cyber", "Lunar", "Pixel", "Shadow", "Iron"};
		String[] b = {"Wolf", "Fox", "Raven", "Blade", "Storm", "Ghost", "Viper", "Hawk", "Tiger", "Knight", "Dragon"};
		Random r = new Random();
		String n = a[r.nextInt(a.length)] + b[r.nextInt(b.length)] + r.nextInt(100);
		return n.length() > 16 ? n.substring(0, 16) : n;
	}

	private JPanel accountsPage() {
		JPanel page = new JPanel(new BorderLayout());
		page.setBackground(BG);
		page.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		Card card = new Card(new BorderLayout(0, 12));
		card.add(label("Nicknames", 22, true, TEXT), BorderLayout.NORTH);

		JList<String> list = new JList<>(nickModel);
		list.setBackground(PANEL2);
		list.setForeground(TEXT);
		list.setSelectionBackground(accent);
		list.setSelectionForeground(new Color(0x14100C));
		list.setFixedCellHeight(28);
		JScrollPane sp = new JScrollPane(list);
		sp.setBorder(null);
		card.add(sp, BorderLayout.CENTER);

		JPanel south = new JPanel();
		south.setOpaque(false);
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		JTextField field = new JTextField();
		style(field);
		field.setMaximumSize(new Dimension(400, 32));
		field.setAlignmentX(0f);
		JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		btns.setOpaque(false);
		btns.setAlignmentX(0f);
		JLabel msg = label(" ", 12, false, DIM);
		btns.add(new Btn("Add", false).onClick(() -> {
			String n = field.getText().trim();
			if (!NICK.matcher(n).matches()) {
				msg.setText("3-16 characters: letters, digits, underscore");
			} else if (cfg.nicks.contains(n)) {
				msg.setText("Already in the list");
			} else {
				cfg.nicks.add(n);
				field.setText("");
				msg.setText("Added " + n);
				cfg.save();
				refreshNicks();
			}
		}));
		btns.add(new Btn("Random", false).onClick(() -> field.setText(randomNick())));
		btns.add(new Btn("Use", false).onClick(() -> {
			String n = list.getSelectedValue();
			if (n != null) {
				cfg.nick = n;
				cfg.save();
				refreshNicks();
				msg.setText("Now playing as " + n);
			}
		}));
		btns.add(new Btn("Remove", false).onClick(() -> {
			String n = list.getSelectedValue();
			if (n != null && cfg.nicks.size() > 1) {
				cfg.nicks.remove(n);
				if (n.equals(cfg.nick)) {
					cfg.nick = cfg.nicks.get(0);
				}
				cfg.save();
				refreshNicks();
			}
		}));
		south.add(label("New nickname", 12, false, DIM));
		south.add(Box.createVerticalStrut(4));
		south.add(field);
		south.add(Box.createVerticalStrut(8));
		south.add(btns);
		south.add(Box.createVerticalStrut(6));
		south.add(msg);
		south.add(label("Offline names: they work in singleplayer and on servers with offline mode.", 11, false, DIM));
		card.add(south, BorderLayout.SOUTH);
		page.add(card, BorderLayout.CENTER);
		return page;
	}

	// ------------------------------------------------------------------ settings

	private JPanel settingsPage() {
		JPanel page = new JPanel(new BorderLayout());
		page.setBackground(BG);
		page.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		Card card = new Card(new BorderLayout(0, 10));
		card.add(label("Settings", 22, true, TEXT), BorderLayout.NORTH);

		JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
		form.setOpaque(false);

		JLabel ramLabel = label(cfg.ramMb + " MB", 13, false, TEXT);
		JSlider ram = new JSlider(1024, 16384, Math.max(1024, Math.min(16384, cfg.ramMb)));
		ram.setOpaque(false);
		ram.setMajorTickSpacing(1024);
		ram.addChangeListener(e -> {
			int v = Math.round(ram.getValue() / 256f) * 256;
			cfg.ramMb = v;
			ramLabel.setText(v + " MB");
			cfg.save();
		});
		form.add(label("Memory (RAM)", 13, false, DIM));
		JPanel ramRow = new JPanel(new BorderLayout(8, 0));
		ramRow.setOpaque(false);
		ramRow.add(ram, BorderLayout.CENTER);
		ramRow.add(ramLabel, BorderLayout.EAST);
		form.add(ramRow);

		form.add(label("Extra JVM arguments", 13, false, DIM));
		JTextField jvm = new JTextField(cfg.jvmArgs);
		style(jvm);
		jvm.addActionListener(e -> save(jvm, null));
		jvm.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				cfg.jvmArgs = jvm.getText();
				cfg.save();
			}
		});
		form.add(jvm);

		form.add(label("Java path (empty = launcher's Java)", 13, false, DIM));
		form.add(pathRow(cfg.javaPath, v -> cfg.javaPath = v, false));

		form.add(label("Game folder (empty = default)", 13, false, DIM));
		form.add(pathRow(cfg.gameDir, v -> cfg.gameDir = v, true));

		form.add(label("Window size", 13, false, DIM));
		JPanel size = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		size.setOpaque(false);
		JSpinner w = new JSpinner(new SpinnerNumberModel(cfg.width, 640, 7680, 10));
		JSpinner h = new JSpinner(new SpinnerNumberModel(cfg.height, 480, 4320, 10));
		w.addChangeListener(e -> {
			cfg.width = (Integer) w.getValue();
			cfg.save();
		});
		h.addChangeListener(e -> {
			cfg.height = (Integer) h.getValue();
			cfg.save();
		});
		JCheckBox full = new JCheckBox("Fullscreen", cfg.fullscreen);
		full.setOpaque(false);
		full.setForeground(TEXT);
		full.addActionListener(e -> {
			cfg.fullscreen = full.isSelected();
			cfg.save();
		});
		size.add(w);
		size.add(label("x", 13, false, DIM));
		size.add(h);
		size.add(full);
		form.add(size);

		form.add(label("After launching", 13, false, DIM));
		JComboBox<String> after = new JComboBox<>(new String[]{"Hide launcher", "Keep open", "Close launcher"});
		after.setSelectedItem(cfg.afterLaunch);
		after.addActionListener(e -> {
			cfg.afterLaunch = (String) after.getSelectedItem();
			cfg.save();
		});
		form.add(after);

		form.add(label("Install Aurora + Fabric API", 13, false, DIM));
		JCheckBox inst = new JCheckBox("Update the mods folder on every launch", cfg.installAurora);
		inst.setOpaque(false);
		inst.setForeground(TEXT);
		inst.addActionListener(e -> {
			cfg.installAurora = inst.isSelected();
			cfg.save();
		});
		form.add(inst);

		form.add(label("Accent color", 13, false, DIM));
		form.add(new Btn("Choose... (restart to apply)", false).onClick(() -> {
			Color c = JColorChooser.showDialog(frame, "Accent color", accent);
			if (c != null) {
				cfg.accent = String.format("%06X", c.getRGB() & 0xFFFFFF);
				cfg.save();
			}
		}));

		card.add(form, BorderLayout.CENTER);

		JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		foot.setOpaque(false);
		foot.add(new Btn("Open game folder", false).onClick(() -> {
			try {
				java.nio.file.Files.createDirectories(cfg.gameDirPath());
				java.awt.Desktop.getDesktop().open(cfg.gameDirPath().toFile());
			} catch (Exception ex) {
				setStatus("Cannot open the folder: " + ex.getMessage(), 0);
			}
		}));
		foot.add(new Btn("Verify files", false).onClick(() -> {
			if (busy) {
				return;
			}
			busy = true;
			playBtn.setActive(false);
			cards.show(content, "play");
			Thread t = new Thread(() -> {
				try {
					new Installer(root).prepare(cfg, this::setStatus, true);
					setStatus("All files verified", 0);
				} catch (Exception ex) {
					setStatus("Failed: " + ex.getMessage(), 0);
				} finally {
					busy = false;
					playBtn.setActive(true);
				}
			}, "verify");
			t.setDaemon(true);
			t.start();
		}));
		card.add(foot, BorderLayout.SOUTH);
		page.add(card, BorderLayout.CENTER);
		return page;
	}

	private void save(JTextField jvm, Object unused) {
		cfg.jvmArgs = jvm.getText();
		cfg.save();
	}

	private interface Setter {
		void set(String v);
	}

	private JPanel pathRow(String initial, Setter setter, boolean dirs) {
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		JTextField f = new JTextField(initial);
		style(f);
		f.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				setter.set(f.getText().trim());
				cfg.save();
			}
		});
		Btn browse = new Btn("Browse", false).onClick(() -> {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(dirs ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
			if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
				f.setText(fc.getSelectedFile().getAbsolutePath());
				setter.set(f.getText());
				cfg.save();
			}
		});
		browse.setPreferredSize(new Dimension(80, 32));
		row.add(f, BorderLayout.CENTER);
		row.add(browse, BorderLayout.EAST);
		return row;
	}
}
