/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.jaredrummler.fastscrollrecyclerview.sample;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jaredrummler.fastscrollrecyclerview.FastScrollRecyclerView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    FastScrollRecyclerView recyclerView = (FastScrollRecyclerView) findViewById(R.id.recycler);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    recyclerView.setAdapter(new RecyclerAdapter(getResources().getStringArray(R.array.countries_array)));
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_github:
        try {
          startActivity(new Intent(Intent.ACTION_VIEW,
              Uri.parse("https://github.com/jaredrummler/FastScroll-RecyclerView")));
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private static class RecyclerAdapter extends RecyclerView.Adapter<RecyclerAdapter.ViewHolder>
      implements FastScrollRecyclerView.SectionedAdapter {

    private final String[] countries;

    public RecyclerAdapter(String[] countries) {
      this.countries = countries;
    }

    @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
    }

    @Override public void onBindViewHolder(ViewHolder holder, int position) {
      holder.text.setText(countries[position]);
    }

    @Override public int getItemCount() {
      return countries.length;
    }

    @NonNull @Override public String getSectionName(int position) {
      return countries[position].substring(0, 1).toUpperCase(Locale.ENGLISH);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

      public TextView text;

      public ViewHolder(View itemView) {
        super(itemView);
        text = (TextView) itemView.findViewById(R.id.text);
      }

    }
  }

}
