package org.servalproject.ui;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class SimpleAdapter<T> extends BaseAdapter {
	final int resourceIds[];
	final ViewBinder<T> binder;
	final LayoutInflater inflater;
	List<T> items;

	public interface ViewBinder<T> {
		long getId(T t);

		int getViewType(T t);

		void bindView(T t, View view);
	}

	public SimpleAdapter(Context context, int resourceIds[],
			ViewBinder<T> binder) {
		this.resourceIds = resourceIds;
		this.inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.binder = binder;
	}

	public SimpleAdapter(Context context, int resourceId, ViewBinder<T> binder) {
		this(context, new int[] {
			resourceId
		}, binder);
	}

	@Override
	public int getCount() {
		return items.size();
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
		return binder.getId(getItem(position));
	}

	@Override
	public int getItemViewType(int position) {
		return binder.getViewType(getItem(position));
	}

	@Override
	public int getViewTypeCount() {
		return this.resourceIds.length;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		T t = getItem(position);
		if (convertView == null)
			convertView = inflater.inflate(resourceIds[binder.getViewType(t)],
					parent, false);

		binder.bindView(t, convertView);
		return convertView;
	}

}
