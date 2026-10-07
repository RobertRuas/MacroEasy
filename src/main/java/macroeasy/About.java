package macroeasy;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class About {
    static final String VERSION = "1.0";

    private About() {}

    static void show(Window owner) {
        JDialog dialog = new JDialog(owner, "Sobre o MacroEasy", JDialog.ModalityType.APPLICATION_MODAL);
        JLabel title = new JLabel("MacroEasy");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        body.add(title, BorderLayout.NORTH);
        body.add(text("""
                Versão %s

                Desenvolvedor
                Robert

                Aplicação de macros por passos: cliques, teclado, esperas e janelas já abertas.

                Atualização
                Uma versão antiga não abre. A release nova é instalada sozinha.
                """.formatted(VERSION)), BorderLayout.CENTER);
        dialog.setContentPane(body);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JLabel text(String value) {
        return new JLabel("<html><body style='width:340px'>"
                + value.replace("&", "&amp;").replace("\n", "<br>")
                + "</body></html>");
    }
}
