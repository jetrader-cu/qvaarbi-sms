package tech.bogomolov.incomingsmsgateway;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador del Registro (spec REQ-004): aplana la lista de {@link MessageLog.Entry}
 * en filas de dos tipos —cabecera de grupo (una por regla/remitente) y entrada—,
 * de modo que el usuario ve su historial "por usuario" agrupado y ordenado por
 * fecha descendente. Las entradas ya vienen ordenadas por {@link MessageLog#getAll}.
 */
public class MessageLogAdapter extends BaseAdapter {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    // Cabecera de grupo: nombre de regla + número de mensajes.
    private static class Header {
        final String ruleName;
        final int count;
        Header(String ruleName, int count) {
            this.ruleName = ruleName;
            this.count = count;
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Object> rows = new ArrayList<>();

    public MessageLogAdapter(Context context, List<MessageLog.Entry> entries) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        buildRows(entries);
    }

    // Agrupa preservando el orden de aparición (las entradas ya están orden desc por
    // fecha), así el grupo de la actividad más reciente queda arriba.
    private void buildRows(List<MessageLog.Entry> entries) {
        Map<String, List<MessageLog.Entry>> groups = new LinkedHashMap<>();
        for (MessageLog.Entry e : entries) {
            String key = e.ruleName == null || e.ruleName.isEmpty() ? e.sender : e.ruleName;
            List<MessageLog.Entry> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(e);
        }
        for (Map.Entry<String, List<MessageLog.Entry>> g : groups.entrySet()) {
            rows.add(new Header(g.getKey(), g.getValue().size()));
            rows.addAll(g.getValue());
        }
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Header ? TYPE_HEADER : TYPE_ENTRY;
    }

    // Las cabeceras no son seleccionables ni clicables.
    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == TYPE_ENTRY;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object row = rows.get(position);
        if (row instanceof Header) {
            return bindHeader((Header) row, convertView, parent);
        }
        return bindEntry((MessageLog.Entry) row, convertView, parent);
    }

    private View bindHeader(Header header, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.message_log_header, parent, false);
        }
        TextView title = view.findViewById(R.id.log_header_title);
        title.setText(context.getString(R.string.log_group_title, header.ruleName, header.count));
        return view;
    }

    private View bindEntry(MessageLog.Entry entry, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.message_log_item, parent, false);
        }

        TextView status = view.findViewById(R.id.log_status);
        status.setText(statusLabel(entry));
        status.setTextColor(ContextCompat.getColor(context, statusColor(entry.status)));

        TextView time = view.findViewById(R.id.log_time);
        time.setText(entry.receivedAt > 0
                ? DateUtils.getRelativeTimeSpanString(entry.receivedAt)
                : "");

        TextView body = view.findViewById(R.id.log_body);
        body.setText(entry.body);

        return view;
    }

    // Etiqueta legible del estado + código HTTP e intentos cuando aportan info.
    private String statusLabel(MessageLog.Entry entry) {
        Resources res = context.getResources();
        String base;
        switch (entry.status) {
            case MessageLog.STATUS_SENT:
                base = res.getString(R.string.log_status_sent);
                break;
            case MessageLog.STATUS_FAILED:
                base = res.getString(R.string.log_status_failed);
                break;
            case MessageLog.STATUS_RETRY:
                base = res.getString(R.string.log_status_retry);
                break;
            default:
                base = res.getString(R.string.log_status_pending);
                break;
        }
        if (entry.httpCode > 0) {
            base += " · HTTP " + entry.httpCode;
        }
        if (entry.attempts > 1) {
            base += " · " + res.getString(R.string.log_attempts, entry.attempts);
        }
        return base;
    }

    private int statusColor(String status) {
        switch (status) {
            case MessageLog.STATUS_SENT:
                return R.color.colorAccent;
            case MessageLog.STATUS_FAILED:
                return R.color.colorDanger;
            case MessageLog.STATUS_RETRY:
                return R.color.colorWarning;
            default:
                return R.color.colorEmptyIcon;
        }
    }
}
