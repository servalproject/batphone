package org.servalproject.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

public class SimpleAdapter<T> extends BaseAdapter {
	private static final String TAG = "SimpleAdapter";
	final int resourceIds[];
	final ViewBinder<T> binder;
	final LayoutInflater inflater;
	List<T> items;

	public interface ViewBinder<T> {
		public long getId(int position, T t);

		public int getViewType(int position, T t);

		public void bindView(int position, T t, View view);

		public int[] getResourceIds();

		public boolean hasStableIds();

		public boolean isEnabled(T t);
	}

	public SimpleAdapter(Context context, ViewBinder<T> binder) {
		this.resourceIds = binder.getResourceIds();
		this.inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.binder = binder;
	}

	@Override
	public int getCount() {
		return (items==null)?0:items.size();
	}

	@Override
	public T getItem(int position) {
		return items.get(position);
	}

	public void setItems(List<T> items) {
		this.items = items;
		this.notifyDataSetChanged();
	}

	@Override
	public long getItemId(int position) {
		return binder.getId(position, getItem(position));
	}

	@Override
	public int getItemViewType(int position) {
		return binder.getViewType(position, getItem(position));
	}

	@Override
	public int getViewTypeCount() {
		return this.resourceIds.length;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		T t = getItem(position);
		if (convertView == null) {
			convertView = inflater.inflate(resourceIds[binder.getViewType(position, t)],
					parent, false);
		}
		convertView.setTag(t);
		binder.bindView(position, t, convertView);
		return convertView;
	}

	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
	}

	@Override
	public boolean hasStableIds() {
		return binder.hasStableIds();
	}

	@Override
	public boolean isEnabled(int position) {
		return binder.isEnabled(getItem(position));
	}
}
